(ns flight.models.currency
  (:refer-clojure :exclude [get find])
  (:require 
        [flight.cache :as cache]
        [korma.core :refer [where values select insert]]
        [korma.db :refer [defdb]])
  (:use [flight.db.core]))

(defn get [id]
  (first
    (select currency
            (where {:id id}))))

(defn all []
  (cache/cache! "curriences" (select currency)))

(defn add! [currencies]
  (insert currency (values currencies)))

(defn find [name]
  (first
    (select currency
            (where {:key name}))))
