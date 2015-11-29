(ns flight.models.fan
  (:refer-clojure :exclude [get])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core]))

(defn get [leader-id user-id]
  (first
    (select fans
            (where {:leader_id leader-id :user_id user-id}))))

(defn exists? [leader-id user-id]
  (not (empty? (get leader-id user-id))))

(defn add! [leader-id user-id]
  (transaction
    (update users
            (set-fields {:fans (raw "fans + 1")})
            (where {:id leader-id}))
    (insert fans (values {:leader_id leader-id :user_id user-id}))))

(defn remove! [leader-id user-id]
  (transaction
    (update users
            (set-fields {:fans (raw "fans - 1")})
            (where {:id leader-id}))
    (delete fans
            (where {:leader_id leader-id :user_id user-id}))))

(defn all [user-id]
  (select fans
          (with users
                (fields :login :alias :listings :rating :banned :last_login))
          (where {:user_id user-id})))
