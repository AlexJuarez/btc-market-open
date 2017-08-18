(ns flight.cache
  (:refer-clojure :exclude [set get namespace])
  (:require [clojurewerkz.spyglass.client :as c]
            [flight.env :refer [env]]
            [flight.util.mem-store :as mem]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log]
            [ring.middleware.session.store :as session-store])
  (:import net.spy.memcached.compat.log.Log4JLogger
           org.apache.log4j.BasicConfigurator))

(defn create-connection [address]
  (if-not (empty? address)
    (try
      (c/text-connection address)
      (catch Exception e
        (log/error "Error creating couchbase connection" e)
        nil))))

(defn init-connection []
  (if (env :couchbase)
    (do
      (let [props (System/getProperties)]
        (.put props "net.spy.log.LoggerImpl" "net.spy.memcached.compat.log.Log4JLogger")
        (System/setProperties props)
        (org.apache.log4j.BasicConfigurator/configure))
      (log/info "Starting couchbase connection")
      (atom (create-connection (env :couchbase-server-uri))))
    (atom nil)))

(defn shutdown-connection [ce]
  (when-not (nil? @ce)
    (log/info "Shutting down couchbase connection")
    (c/shutdown @ce)))

(defstate ^:dynamic *ce*
  :start (init-connection)
  :stop (shutdown-connection *ce*))

(defn get-connection []
  (cast net.spy.memcached.MemcachedClient @*ce*))

(defrecord CouchBaseSessionStore [namespace ttl-secs]
  session-store/SessionStore
  (read-session [_ key] (or (when key (c/get (get-connection) (str namespace key))) {}))
  (delete-session [_ key] (c/delete (get-connection) (str namespace key)) nil)
  (write-session [_ key data]
    (let [key (or key (str (java.util.UUID/randomUUID)))]
      (c/set (get-connection) (str namespace key) ttl-secs data)
      key)))

(defn create-couchbase-session-store
  ([]
   (create-couchbase-session-store "session:"))
  ([namespace]
   (->CouchBaseSessionStore namespace (* 60 60 10))))

(defn set [key value & ttl]
  (mem/set key value)
  (when-not (nil? @*ce*)
    (c/set (get-connection) key (or (first ttl) (+ (* 60 10) (rand-int 600))) value)))
    ;;rand-int adds variation on key expiration

(defn get [key]
  (let [mem-val (mem/get key)]
    (if mem-val
      mem-val
      (when-not (nil? @*ce*)
        (when-let [val (c/get (get-connection) key)]
          (mem/set key val)
          val)))))

(defn delete [key]
  (if-not (nil? @*ce*)
    (do
      (log/info key "deleted from cache")
      (c/delete (get-connection) key))
    (mem/delete key)))

(defmacro cache! [key & forms]
  `(let [value# (get ~key)]
     (if (nil? value#)
       (let [v# (do ~@forms)]
         (when-not (nil? v#) (set ~key v#))
         v#)
       value#)))
