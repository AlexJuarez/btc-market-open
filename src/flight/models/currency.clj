(ns flight.models.currency
  (:refer-clojure :exclude [get find])
  (:require
        [flight.cache :as cache]
        [flight.util.mem-store :as mem]
        [korma.core :refer [where values select insert]]
        [korma.db :refer [defdb]]
        [flight.db.predicates :refer :all]
        [mount.core :refer [defstate]])
  (:use [flight.db.core]))

(defn all
  ([cache?] (select currency))
  ([]
    (cache/cache! "currencies" (select currency))))

(defn get [id]
  (first
    (select currency
            (where {:id id}))))

(defn exists? [id]
  (not (nil? (get id))))

(defn add! [currencies]
  (insert currency (values currencies)))

(defn find [name]
  (cache/cache! (str "currency:" key)
                (first
                  (select currency
                          (where {:key [ilike name]})))))
