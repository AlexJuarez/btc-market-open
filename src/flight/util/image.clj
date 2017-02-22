(ns flight.util.image
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]
    [flight.env :refer [env]]
    [image-resizer.fs :as fs]
    [clojure.java.io :as io])
  (:use hiccup.core)
  (:import
    [java.io File FileInputStream FileOutputStream]
    [org.apache.commons.io IOUtils]
    [org.apache.commons.codec.binary Base64]
    [javax.imageio ImageIO]))

(defn resource-path []
  (if-let [path (io/resource "public/")]
    (str (.getPath path) "uploads/")))

(defn file-path [id &suffix]
  (str (resource-path)
       id
       &suffix))

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
    (if (env :embed-image)
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

(defn- upload-path [filename]
  (let [path (java.net.URLDecoder/decode filename "utf-8")
        file (new java.io.File (str (resource-path) path))]
     (.mkdirs (.getParentFile file))
     (.getPath file)))

(defn upload-file
  "uploads a file to the target folder"
  [{:keys [tempfile size filename]}]
  (try
    (with-open [in (new FileInputStream tempfile)
                out (new FileOutputStream (upload-path filename))]
      (let [source (.getChannel in)
            dest (.getChannel out)]
        (.transferFrom dest source 0 (.size source))
        (.flush out)))))

(defn save-file [buffered-file path]
  (ImageIO/write buffered-file (fs/extension path) (File. path)))
