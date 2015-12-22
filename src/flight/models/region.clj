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

(defn- *all []
  (select region))

(defn all []
  (cache/cache! "regions" *all))

(defn add! [regions]
  (insert region (values regions)))
