(ns flight.routes.helpers
  (:require
    [image-resizer.core :as resizer]
    [image-resizer.format :as format]
    [flight.models.image :as image]
    [flight.util.image :refer [resource-path upload-file save-file]]))

(defn parse-image [image_id image]
  (if (or (nil? image) (= 0 (:size image)))
    image_id
    (if (and (< (:size image) 800000) (not (empty? (re-find #"jpg|jpeg" (string/lower-case (:filename image))))))
      (let [image_id (:id (image/add! (user-id)))]
        (try
          (do
            (upload-file (assoc image :filename (str image_id ".jpg")))
            (save-file (resizer/resize-and-crop (clojure.java.io/file (str (resource-path) "/" image_id ".jpg")) 400 300) (str (resource-path) "/" image_id "_max.jpg"))
            (save-file (resizer/resize-and-crop (clojure.java.io/file (str (resource-path) "/" image_id ".jpg")) 180 135) (str (resource-path) "/" image_id "_thumb.jpg"))
            (clojure.java.io/delete-file (str (resource-path) "/" image_id ".jpg")))
          (catch Exception ex
            (log/error ex (str "File upload failed for image " image_id))))
          image_id))))
