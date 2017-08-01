(ns flight.routes.helpers
  (:require
    [flight.models.report :as report]
    [ring.util.response :as resp]))

(defn report-add [object-id user-id table referer]
  (report/add! object-id user-id table)
  (resp/redirect referer))

(defn report-remove [object-id user-id table referer]
  (report/remove! object-id user-id table)
  (resp/redirect referer))
