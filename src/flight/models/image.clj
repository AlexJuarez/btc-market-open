(ns flight.models.image
  (:refer-clojure :exclude [get update])
  (:require
   [flight.util.image :as image-util]
   [taoensso.timbre :as log])
  (:use [korma.db :only (defdb)]
        [korma.core]
        [clojure.java.io :as io]
        [flight.db.core]))

(defn all [user-id]
  (select images
          (where {:user_id user-id})))

(defn add! [user-id]
  (insert images
          (values {:user_id user-id})))

(defn get
  [id user-id]
  (first (select images
                 (where {:user_id user-id :id id}))))

(defn exists? [id user-id]
  (not (nil? (get id user-id))))

(defn remove! [id user-id]
  (when-let [image (get id user-id)]
    (try
      (do
        (io/delete-file (image-util/file-path id "_max.jpg"))
        (io/delete-file (image-util/file-path id "_thumb.jpg")))
      (catch Exception ex
        (log/error "failed to delete image" id)))
      (delete images
              (where {:user_id user-id :id id}))))

(defn update! [id data user-id]
  (update images
          (set-fields data)
          (where {:id id :user_id user-id}))
  (get id user-id))
