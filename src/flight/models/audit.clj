(ns flight.models.audit
  (:refer-clojure :exclude [get])
  (:use
    [korma.core]
    [flight.db.core]))

(defn all [user-id]
  (select audits
          (where {:user_id user-id})
          (limit 20)
          (order :created_on :desc)))
