(ns flight.models.escrow
  (:refer-clojure :exclude [get update])
  (:use
    [korma.core]
    [flight.db.core]))

(defn all []
  (select escrow
          (limit 20)
          (order :created_on :desc)))
