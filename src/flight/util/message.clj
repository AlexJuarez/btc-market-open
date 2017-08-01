(ns flight.util.message
  (:refer-clojure :exclude [get])
  (:require
    [flight.util.session :as session]))

(defn get []
  (session/flash-get :message))

(defn raw! [msg t]
  (session/flash-put! :message {:content msg :type t}))

(defn warn! [msg]
  (raw! msg :warn))

(defn success! [msg]
  (raw! msg :success))

(defn error! [msg]
  (raw! msg :error))
