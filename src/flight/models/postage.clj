(ns flight.models.postage
  (:refer-clojure :exclude [get count update])
  (:use [korma.db :only (defdb)]
        [korma.core]
        [flight.db.core])
  (:require
        [flight.util.core :as util]
        [flight.util.error :as error]))

(defn convert [postages]
  (map #(assoc % :price (util/convert-currency %)) postages))

(defn all [user-id]
  (select postage
      (with currency
            (fields [:name :currency_name] [:symbol :currency_symbol]))
      (where {:user_id user-id})))

(defn public [user-id]
  (convert (select postage
      (where {:user_id user-id}))))

(defn get
  ([id]
   (first (select postage
      (where {:id id}))))
  ([id user-id]
    (first (select postage
      (where {:id id :user_id user-id})))))

(defn exists? [id]
  (not (nil? (get id))))

(defn remove! [id user-id]
  (delete postage
    (where {:id id :user_id user-id})))

(defn prep [{:keys [title price currency_id] :as item}]
  (merge item
         {:title title
          :currency_id currency_id
          :updated_on (raw "now()")}))

(defn store! [post user-id]
  (insert postage (values (assoc (prep post) :user_id user-id))))

(defn add! [post user-id]
  (if (error/empty?)
    (store! post user-id)
    post))

(defn update! [post id user-id]
  (if (error/empty?)
    (do
      (update postage
          (set-fields (prep post))
          (where {:id id :user_id user-id}))
      (get id user-id))
    post))

(defn count [id]
  (:cnt (first (select postage
    (aggregate (count :*) :cnt)
    (where {:user_id id})))))
