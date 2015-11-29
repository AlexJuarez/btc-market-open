(ns flight.util.mem-store
  (:refer-clojure :exclude [get set]))

(defonce mem (atom {}))

(defn get [key]
  (clojure.core/get @mem key))

(defn set [key value]
  (swap! mem assoc key value))

(defn delete [key]
  (swap! mem dissoc key))
