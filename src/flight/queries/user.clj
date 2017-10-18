(ns flight.queries.user
  (:refer-clojure :exclude [get update])
  (:require
    [flight.db.core :refer :all]
    [korma.core :refer :all]
    [korma.db :refer [transaction]]
    [flight.db.predicates :refer [ilike]]
    [korma.db :only (transaction)]
    [clojure.string :as s]))

(defmacro get [& body]
  `(->
     (select* users)
     ~@body
     (with currency (fields [:key :currency_key] [:symbol :currency_symbol]))
     (limit 1)
     select
     first))

(defn find-by-alias [query]
  (select users
          (fields :alias :fans :last_login :rating :listings :id :vendor)
          (where {:alias [ilike query] :vendor true :admin false})
          (limit 50)))

(defn get-by-id [id]
  (get
    (where {:id id})))

(defn get-by-login [login]
  (get
    (where {:login (s/lower-case login)
            :banned false})))

(defn get-by-alias [alias]
  (get
    (where {:alias alias})))

(defn get-with-pin [id pin]
  (get
    (fields :login)
    (where {:id id
            :pin pin})))

(defn vendor-list []
  (select users
          (fields [:pub_key_id :PGPKeyID] :alias [:created_on :joined] :transactions :rating)
          (where {:vendor true :pub_key_id [not= nil]})))

(defn update! [updates id]
  (->
    (update* users)
    (set-fields updates)
    (where {:id id})
    (update)))

(defn reset-login-count [id]
  (update! {:last_attempted_login (raw "now()") :login_tries 1} id))

(defn inc-login-count [id]
  (update! {:login_tries (raw "login_tries + 1")} id))

(defn login-success [session id]
  (update users (set-fields {:session nil}) (where {:session session}))
  (update! {:login_tries 0 :last_login (raw "now()") :session session} id))

(defn update-pin! [pin id]
  (update! {:pin pin} id))

(defn update-btc-address! [btc-address id]
  (transaction
    (insert wallets (values {:wallet btc-address :user_id id}))
    (update! {:wallet btc-address} id)))

(defn withdraw-btc! [amount address pin user-id]
  (let [audit {:user_id user-id :role "withdrawal" :amount (* -1 amount)}]
    (transaction
      (update users (set-fields {:btc (raw (str "btc - " amount))}) (where {:id user-id}))
      (insert withdrawals (values {:amount amount :address address :user_id user-id}))
      (insert audits (values audit)))))

(defn add-wallet! [id wallet privkey]
  (insert wallets (values {:wallet wallet :privkey privkey :user_id id}))
  (update! {:wallet wallet} id))

(defn create! [user]
  (insert users (values user)))

(defn remove! [login]
  (delete users
          (where {:login login})))
