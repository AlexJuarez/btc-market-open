(ns flight.models.user
  (:refer-clojure :exclude [get])
  (:import (org.apache.commons.codec.binary Base64))
  (:use [flight.db.core]
        [flight.db.predicates]
        [korma.db :only (transaction)]
        [korma.core])
  (:require
        [flight.util.btc :as btc]
        [flight.validator :as v]
        [hiccup.util :as hc]
        [clojure.string :as s]
        [flight.util.core :as util]
        [flight.util.user :as user-util]
        [flight.util.pgp :as pgp]
        [noir.session :as session]
        [flight.models.order :as order]
        [flight.models.message :as message]
        [flight.models.currency :as currency]
        [noir.util.crypt :as warden]))

(def ^:private salt-byte-size 24)

;; Gets

(first (select users))

(defn search [query]
  (select users
          (fields :alias :fans :last_login :rating :listings :id :vendor)
          (where {:alias [ilike query] :vendor true :admin false})
          (limit 50)))

(defn get-dirty [id]
  (first (select users
                 (where {:id id}))))

(defn make-salt []
  (let [b (byte-array salt-byte-size)
        ran (java.security.SecureRandom.)]
    (do (.nextBytes ran b)
      (.toString (Base64/encodeBase64String b)))))

(defn get-by-login [login]
  (first
    (select users
      (with currency (fields [:key :currency_key] [:symbol :currency_symbol]))
      (where {:login (s/lower-case login) :banned false}))))

(defn track-login [{:keys [id last_attempted_login login_tries] :as user}]
  (if (or (= login_tries 0) (nil? last_attempted_login) (> (- (.getTime (java.util.Date.)) (.getTime last_attempted_login)) 86400000))
    (update users
            (set-fields {:last_attempted_login (raw "now()") :login_tries 1})
            (where {:id id}))
    (update users
            (set-fields {:login_tries (raw "login_tries + 1")})
            (where {:id id})))
   user)

(defn get-by-alias [a]
  (first (select users
          (where {:alias a}))))

(defn get [i]
  (dissoc
   (if
    (instance? Long i)
    (-> (select users
                (where {:id id}))
        first)
    (get-by-alias i)) :salt :pass))

(defn get-with-pin [id pin]
  (first (select users
          (fields :login)
          (where {:id id :pin pin}))))

(defn vendor-list []
  (select users
          (fields [:pub_key_id :PGPKeyID] :alias [:created_on :joined] :transactions :rating)
          (where {:vendor true :pub_key_id [not= nil]})))

;; Mutations and Checks

(defn prep [{pass :pass :as user}]
  (let [salt (make-salt)]
    (assoc user :salt salt :pass (warden/encrypt (str pass salt)))))

(defn valid-user? [{:keys [login pass confirm] :as user}]
  (v/user-validator user))

(defn valid-update? [user]
  (v/user-update-validator user))

(defn clean [{:keys [alias region_id auth currency_id pub_key description]}]
  {:auth (= auth "true")
   :currency_id currency_id
   :region_id region_id
   :description (hc/escape-html description)
   :updated_on (raw "now()")
   :alias alias})

(defn clean-pgp [pub_key]
  {:pub_key (if (empty? pub_key) nil (clojure.string/trim pub_key))
   :pub_key_id (if (empty? pub_key) nil (pgp/get-id pub_key))})

(defn valid-pgp? [user]
  (when (and (not (empty? (:pub_key user))) (nil? (pgp/get-key-ring (:pub_key user)))) {:pub_key "Invalid pgp key"}))

;; Operations

(defn update-pin! [id slug]
  (let [user (user-util/current)
        check (v/user-pin-validator slug)]
    (if (empty? check)
      (do
        (let [user (user-util/current)]
          (update users
                  (set-fields {:pin (:pin slug)})
                  (where {:id id}))
          (session/put! :user (assoc user :pin (:pin user))))
      {})
      {:errors check}
      )))

(defn update! [id slug]
  (let [updates (clean slug)
        check (valid-update? updates)]
    (if (empty? check)
      (let [user (user-util/current)]
        (session/put! :user
                      (merge
                       (update users
                        (set-fields updates)
                        (where {:id id}))
                       (if-not (= (:curreny_id updates) (:currency_id user))
                         {:currency_symbol (:symbol (currency/get (:currency_id updates)))}))))
      {:errors check})))

(defn update-pgp! [pub_key]
  (let [updates (clean-pgp pub_key)
        check (valid-pgp? updates)]
    (if (empty? check)
      (let [user (user-util/current)
            update  (update users
                            (set-fields updates)
                            (where {:id (:id user)}))]
        (session/put! :user (assoc user :pub_key (update :pub_key))))
      {:errors check})))

(defn update-btc-address! [id]
  (let [new-address (btc/newaddress id)]
    (session/put! :user (merge (user-util/current-user) {:wallet new-address}))
    (transaction
      (insert wallets (values {:wallet new-address :user_id id}))
      (update users (set-fields {:wallet new-address}) (where {:id id})))))


;;withdraw for the current user
(defn withdraw-btc! [{:keys [amount address pin] :as slug} user-id]
  (let [audit {:user_id user-id :role "withdrawal" :amount (* -1 amount)}
        errors (v/user-withdrawal-validator slug)]
    (if (empty? errors)
      (do
        (transaction
         (update users (set-fields {:btc (raw (str "btc - " amount))}) (where {:id user-id}))
         (insert withdrawals (values {:amount amount :address address :user_id user-id}))
         (insert audits (values audit)))
        (user-util/update-session user-id))
      {:errors errors})
    ))

(defn update-password! [id {:keys [pass newpass confirm]}]
  (let [user (get-dirty id)]
    (if (and (not (nil? user)) (and (:pass user) (warden/compare (str pass (:salt user)) (:pass user))))
      (let [check (v/user-update-password-validator {:pass newpass :confirm confirm})]
        (if (empty? check)
          (do (update users (set-fields {:pass (warden/encrypt (str newpass (:salt user)))}) (where {:id id}))
            {})
          check))
      {:pass ["Your password is incorrect."]})))

(defn store! [user]
  (let [new-user (insert users (values user))
        user-id (:id new-user)
        wallet (btc/address user-id)
        privkey (btc/privkey wallet)]
    (insert wallets (values {:wallet wallet :privkey privkey :user_id user-id}))
    (update users (set-fields {:wallet wallet}) (where {:id user-id}))))

(defn add! [{:keys [login pass confirm] :as user}]
  (let [check (valid-user? user)]
    (if (empty? check)
      (-> {:login (s/lower-case login) :alias login :currency_id (:id (currency/find "BTC")) :pass pass :vendor true} (prep) (store!))
      {:errors check})))

(defn last-login [id session]
  (transaction
    (update users (set-fields {:session nil}) (where {:session (util/create-uuid session)}))
    (update users
            (set-fields {:login_tries 0 :last_login (raw "now()") :session (util/create-uuid session)})
            (where {:id id}))))

(defn login! [{:keys [login pass session] :as user}]
 (let [userstore (track-login (get-by-login login))]
    (if (nil? userstore)
      (assoc user :error "Username does not exist.")
      (if (> 20 (:login_tries userstore))
        (if (and (:pass userstore) (warden/compare (str pass (:salt userstore)) (:pass userstore)))
            (do (last-login (:id userstore) session) (dissoc userstore :salt :pass))
            (assoc user :error "Password Incorrect."))
        (assoc user :error "This account has been locked for failing to login too many times.")))))

(defn remove! [login]
  (delete users
          (where {:login login})))
