(ns flight.models.image
  (:refer-clojure :exclude [get])
  (:use [korma.db :only (defdb)]
        [noir.io :only (resource-path)]
        [korma.core]
        [clojure.java.io :as io]
        [flight.util :as util]
        [flight.db.core]))

(defn add! [user-id]
  (insert images (values {:user_id user-id}))) 

(defn get 
  ([user-id]
    (select images
            (where {:user_id user-id})))
  ([id user-id]
   (first (select images
           (where {:user_id user-id :id (util/parse-int id)})))))

(defn remove! [id user-id]
  (let [id (util/parse-int id)]
    (if-let [image (get id user-id)]
      (do 
        (try
          (io/delete-file (str (resource-path) "uploads/" id "_max.jpg"))
          (io/delete-file (str (resource-path) "uploads/" id "_thumb.jpg"))
          (catch Exception ex))
        (delete images
                (where {:user_id user-id :id id}))))))

(defn update! [id data]
  (let [id (util/parse-int id)]
    (update images
            (set-fields data)
            (where {:id id}))))
