(ns flight.models.postage
  (:refer-clojure :exclude [get count])
  (:use [korma.db :only (defdb)]
        [korma.core]
        [flight.db.core])
  (:require
        [flight.validator :as v]
        [flight.util :as util]))

(defn convert [postages]
  (map #(assoc % :price (util/convert-currency %)) postages))

(defn all [user-id]
  (select postage
      (with currency
            (fields [:name :currency_name] [:symbol :currency_symbol]))
      (where {:user_id (util/parse-int user-id)})))

(defn public [user-id]
  (convert (select postage
      (where {:user_id (util/parse-int user-id)}))))

(defn get
  ([id]
   (first (select postage
      (where {:id (util/parse-int id)}))))
  ([id user-id]
    (first (select postage
      (where {:id (util/parse-int id) :user_id user-id})))))

(defn remove! [id user-id]
  (delete postage
    (where {:id (util/parse-int id) :user_id user-id})))

(defn prep [{:keys [title price currency_id]}]
  {:title title
   :price (util/parse-float price)
   :currency_id (util/parse-int currency_id)
   :updated_on (raw "now()")})

(defn store! [post user-id]
  (insert postage (values (assoc (prep post) :user_id user-id))))

(defn add! [post user-id]
  (let [check (v/postage-validator post)]
    (if (empty? check)
      (store! post user-id)
      (conj {:errors check} post))))

(defn update! [post id user-id]
  (let [check (v/postage-validator post)]
    (if (empty? check)
      (update postage
        (set-fields (prep post))
       (where {:id (util/parse-int id) :user_id user-id}))
      (conj {:errors check} post))))

(defn count [id]
  (:cnt (first (select postage
    (aggregate (count :*) :cnt)
    (where {:user_id id})))))
