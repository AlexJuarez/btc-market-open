(ns flight.util.mem-store
  (:require [mount.core :refer [defstate]])
  (:refer-clojure :exclude [get set]))

(defstate mem
  :start (atom {}))

(defn now [ttl]
  (+ (/ (System/currentTimeMillis) 1000) ttl))

(defn delete [key]
  (swap! mem dissoc key))

(defn get [key]
  (let [{:keys [ttl value]} (clojure.core/get @mem key)]
    (if (or (nil? value) (>= (now 0) ttl))
      (do
        (delete key)
        nil)
      value)))

(defn set [key value]
  (swap! mem assoc key {:value value :ttl (now 60)}))

