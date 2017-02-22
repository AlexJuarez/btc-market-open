(ns flight.models.region
  (:refer-clojure :exclude [get])
  (:require
   [korma.db :refer [defdb]]
   [korma.core :refer [select where insert values delete]]
   [flight.db.core :refer [region]]
   [flight.cache :as cache]))

(defn get [id]
  (first
   (select region
           (where {:id id}))))

(defn exists? [id]
  (not (nil? (get id))))

(defn all []
  (cache/cache! "region/all" (select region)))

(defn add! [regions]
  (insert region (values regions)))
