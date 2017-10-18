(ns flight.queries.currency
  (:refer-clojure :exclude [get update])
  (:require
    [flight.db.core :refer :all]
    [korma.core :refer :all]))

(defn all []
  (select currency))

(defn get [id]
  (->
    (select* currency)
    (where {:id id})
    (limit 1)
    select
    first))

(defn add! [currencies]
  (insert currency (values currencies)))

(defn get-by-name [name]
  (->
    (select* currency)
    (where {:key (clojure.string/upper-case name)})
    (limit 1)
    (select)
    first))
