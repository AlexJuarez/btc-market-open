(ns flight.models.user
  (:refer-clojure :exclude [get update])
  (:require
    [flight.queries.user :as users]
    [flight.cache :as cache]
    [flight.util.btc :as btc]
    [hiccup.util :as hc]
    [clojure.string :as s]
    [flight.util.core :as util]
    [flight.util.pgp :as pgp]
    [flight.util.session :as session]
    [flight.models.currency :as currency]
    [flight.util.crypt :as warden]))

(defn prep [{pass :pass :as user}]
    (assoc user :pass (warden/encrypt pass)))

(defn clean [{:keys [alias region_id auth currency_id pub_key description]}]
  {:auth (= auth "true")
   :currency_id currency_id
   :region_id region_id
   :description (hc/escape-html description)
   :alias alias})

(defn clean-pgp [pub_key]
  {:pub_key (when-not (empty? pub_key) (clojure.string/trim pub_key))
   :pub_key_id (when-not (empty? pub_key) (pgp/get-id pub_key))})

(defn search [query]
  (users/find-by-alias query))

(defn get-by-login [login]
  (users/get-by-login login))

(defn get-by-alias [a]
  (users/get-by-alias a))

(defn alias-availible? [a user-id]
  (let [user (get-by-alias a)]
    (or (nil? user)
        (= user-id (:id user)))))

(defn get [id]
  (->
    (if
      (number? id)
      (users/get-by-id id)
      (users/get-by-login id))
    (dissoc :pass)))

(defn exists? [login]
  (cache/cache!
    (str "user/exists?:" login)
    (not (nil? (get login)))))

(defn track-login [{:keys [id last_attempted_login login_tries]}]
  (if
    (or
      (= login_tries 0)
      (nil? last_attempted_login)
      (> (- (.getTime (java.util.Date.))
            (.getTime last_attempted_login)) 86400000))
    (users/reset-login-count id)
    (users/inc-login-count id)))

(defn get-with-pin [id pin]
  (users/get-with-pin id pin))

(defn vendor-list []
  (users/vendor-list))

(defn update-pin! [{:keys [pin]} user-id]
  (users/update-pin! pin user-id)
  (session/assoc-in! [:user :pin] pin))

(defn update! [id slug]
  (let [updates (clean slug)]
    (users/update! updates id)
    (session/put! :user (users/get-by-id id))))

(defn update-pgp! [pub_key user-id]
  (let [updates (clean-pgp pub_key)]
    (users/update! updates user-id)
    (session/assoc-in! [:user :pub_key] pub_key)))

(defn update-btc-address! [id]
  (let [new-address (btc/newaddress id)]
    (session/assoc-in! [:user :wallet] new-address)
    (users/update-btc-address! new-address id)))

(defn withdraw-btc! [{:keys [amount address pin] :as slug} user-id]
  (users/withdraw-btc! amount address pin user-id)
  (util/update-session user-id))

(defn update-password! [id {:keys [pass newpass confirm]}]
  (when-let [user (users/get-by-id id)]
    (when (warden/compare pass (:pass user))
      (users/update! {:pass (warden/encrypt newpass)} id))))

(defn store! [user]
  (let [{:keys [id]} (users/create! user)
        wallet (btc/address id)
        privkey (btc/privkey wallet)]
    (users/add-wallet! id wallet privkey)))

(defn add! [{:keys [login pass confirm]}]
  (-> {:login (s/lower-case login)
       :alias login
       :currency_id (:id (currency/find "BTC"))
       :pass pass
       :vendor true}
      (prep)
      (store!)))

(defn login! [login pass session]
  (when-let [user (users/get-by-login login)]
    (when (and
            (> 20 (:login_tries user))
            (not (nil? (:pass user)))
            (warden/compare pass (:pass user)))
      (users/login-success (util/create-uuid session) (:id user))
      (dissoc user :pass))))

(defn remove! [login]
  (users/remove! login))
