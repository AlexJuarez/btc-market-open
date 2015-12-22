(ns flight.util.image
  (:require
    [clojure.java.io :as io])
  (:use hiccup.core)
  (:import
    (org.apache.commons.io IOUtils)
    (org.apache.commons.codec.binary Base64)))


(defn file-path [id &suffix]
  (str (clojure.java.io/resource "uploads")
       id
       &suffix))

;;this means that we will use a data;mime type to embed the image instead
(def img-data true)

(defn read-image [id]
  (let [path (file-path id ".jpg")
        file (io/file path)]
    (if (.exists file)
      (with-open [in (io/input-stream file)]
        (.toString (Base64/encodeBase64String (IOUtils/toByteArray in)))))))

(defn image-data [id extension]
  (let [filename (str id extension)
        data (read-image filename)]
    (if data
      (str "data:image/jpeg;base64," data))))

(defn create-image [id extension]
    (if img-data
      (let [data (image-data id extension)]
        (html [:img {:src data}]))
      (let [url (str "/uploads/" id extension ".jpg")]
        (html [:img {:src url}]))))

(defn img [url title alt]
  (let [url (re-find #"\d+" url)
        data (image-data url "_max")]
    (if data
      (html [:img {:src (image-data url "_max") :title title :alt alt}])
      (html [:span {:class "warn"} (str "invalid image " url)]))))
