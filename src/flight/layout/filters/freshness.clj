(ns flight.layout.filters
  (:require
    [selmer.filters :refer :all]
    [hiccup.core :refer [html]])
  (:import
    (java.util.concurrent TimeUnit)))

(defn- ->seconds [m]
  (.toSeconds TimeUnit/MILLISECONDS m))

(defn- ->days [m]
  (.toDays TimeUnit/SECONDS m))

(defn- get-diff [inst]
  (-> (java.util.Date.)
      .getTime
      (- (.getTime inst))
      ->seconds))

(defn- relative-time [inst]
  (let [diff (get-diff inst)
        days (->days diff)
        months (-> days (/ 30) int)]
    (prn days)
    (cond
      (< days 1) "today"
      (< months 1) (if (= days 1) "yesterday" (str days " days ago"))
      :else
      (if (= months 1) "1 month ago" (str months " months ago"))
    )))

(add-filter! :freshness relative-time)
