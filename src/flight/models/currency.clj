(ns flight.models.currency
  (:refer-clojure :exclude [get find])
  (:require
        [flight.cache :as cache]
        [flight.queries.currency :as currency]))

(defn all
  ([cache?] (currency/all))
  ([]
    (cache/cache! "currencies" (currency/all))))

(defn get [id]
  (cache/cache!
    (str "currency:" id)
    (currency/get id)))

(defn exists? [id]
  (-> (get id)
      nil?
      not))

(defn add! [currencies]
  (currency/add! currencies))

(defn find [name]
  (cache/cache!
    (str "currency:" key)
    (currency/get-by-name name)))
