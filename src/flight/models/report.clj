(ns flight.models.report
  (:refer-clojure :exclude [get])
  (:use [korma.core]
        [flight.db.core])
  (:require
        [flight.util :as util]))

(defn get [object-id user-id table]
  (first
    (select reports
            (where {:object_id (util/parse-int object-id) :user_id user-id :type table}))))

(defn reported? [object-id user-id table]
  (not (empty? (get object-id user-id table))))

(defn add! [object-id user-id table]
    (try
      (insert reports
            (values {:object_id (util/parse-int object-id) :user_id user-id :type table}))
      (catch Exception e)))

(defn remove! [object-id user-id table]
  (delete reports
          (values {:object_id (util/parse-int object-id) :user_id user-id :type table})))
