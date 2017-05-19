(ns flight.models.user
  (:refer-clojure :exclude [get update])
  (:import (org.apache.commons.codec.binary Base64))
  (:use [flight.db.core]
        [flight.db.predicates]
        [korma.db :only (transaction)]
        [korma.core])
  (:require
    [flight.cache :as cache]
    [flight.util.btc :as btc]
    [flight.validator :as v]
    [hiccup.util :as hc]
    [clojure.string :as s]
    [flight.util.core :as util]
    [flight.util.error :as error]
    [flight.util.pgp :as pgp]
    [flight.util.session :as session]
    [flight.models.order :as order]
    [flight.models.message :as message]
    [flight.models.currency :as currency]
    [flight.util.crypt :as warden]))

;; Gets

(defn search [query]
  (select users
          (fields :alias :fans :last_login :rating :listings :id :vendor)
          (where {:alias [ilike query] :vendor true :admin false})
          (limit 50)))

(defn get-dirty [id]
  (first (select users
                 (where {:id id}))))

(defn get-by-login [login]
  (first
    (select users
      (with currency (fields [:key :currency_key] [:symbol :currency_symbol]))
      (where {:login (s/lower-case login) :banned false})
            (limit 1))))

(defn get-by-alias [a]
  (first (select users
          (where {:alias a}) (limit 1))))

(defn alias-availible? [a user-id]
  (let [user (get-by-alias a)]
    (or (nil? user) (= user-id (:id user)))))

(defn get [id]
  (dissoc
   (if
    (instance? Long id)
    (-> (select users
                (where {:id id}))
        first)
    (get-by-login id)) :pass))

(defn exists? [login]
  (cache/cache! (str "user/exists?:" login)
                (not (nil? (get login)))))

(defn track-login [{:keys [id last_attempted_login login_tries] :as user}]
  (if (or (= login_tries 0) (nil? last_attempted_login) (> (- (.getTime (java.util.Date.)) (.getTime last_attempted_login)) 86400000))
    (update users
            (set-fields {:last_attempted_login (raw "now()") :login_tries 1})
            (where {:id id}))
    (update users
            (set-fields {:login_tries (raw "login_tries + 1")})
            (where {:id id})))
   user)

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
    (assoc user :pass (warden/encrypt pass)))

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

;; Operations

(defn update-pin! [{:keys [pin] :as slug} user-id]
  (let [check (v/user-pin-validator slug)]
    (if (empty? check)
      (do
        (update users
                  (set-fields {:pin pin})
                  (where {:id user-id}))
        (session/assoc-in! [:user :pin] pin)
        {})
      {:errors check}
      )))

(defn update! [id slug]
  (let [updates (clean slug)]
    (if (error/empty?)
      (let [user (util/current-user)]
        (session/put! :user
                      (merge
                       (transaction
                         (update users
                                 (set-fields updates)
                                 (where {:id id}))
                         (first (select users (where {:id id}) (limit 1))))
                       (if-not (= (:curreny_id updates) (:currency_id user))
                         {:currency_symbol (:symbol (currency/get (:currency_id updates)))}))))
      slug)))

(defn update-pgp! [pub_key user-id]
  (let [updates (clean-pgp pub_key)]
    (update users
            (set-fields updates)
            (where {:id user-id}))
    (->> (select users (where {:id user-id}))
        :pub_key
        (session/assoc-in! [:user :pub_key]))))

(defn update-btc-address! [id]
  (let [new-address (btc/newaddress id)]
    (session/put! :user (merge (util/current-user) {:wallet new-address}))
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
        (util/update-session user-id))
      {:errors errors})
    ))

(defn update-password! [id {:keys [pass newpass confirm]}]
  (let [user (get-dirty id)]
    (if (and (not (nil? user)) (and (:pass user) (warden/compare pass (:pass user))))
      (let [check (v/user-update-password-validator {:pass newpass :confirm confirm})]
        (if (empty? check)
          (do (update users (set-fields {:pass (warden/encrypt newpass)}) (where {:id id}))
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
    (if (and (error/empty?)
             (empty? check))
      (-> {:login (s/lower-case login)
           :alias login
           :currency_id (:id (currency/find "BTC"))
           :pass pass
           :vendor true}
          (prep)
          (store!))
      (error/set! check))))

(defn last-login [id session]
  (transaction
    (update users (set-fields {:session nil}) (where {:session (util/create-uuid session)}))
    (update users
            (set-fields {:login_tries 0 :last_login (raw "now()") :session (util/create-uuid session)})
            (where {:id id}))))

(defn login! [login pass session]
  (when-let [userstore (track-login (get-by-login login))]
    (if (> 20 (:login_tries userstore))
      (if (and
           (not (nil? (:pass userstore)))
           (warden/compare pass (:pass userstore)))
        (do (last-login (:id userstore) session) (dissoc userstore :pass))
        (error/put! :pass "Password Incorrect."))
      (error/put! :message "This account has been locked for failing to login too many times."))))

(defn remove! [login]
  (delete users
          (where {:login login})))
