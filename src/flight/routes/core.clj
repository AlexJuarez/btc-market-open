(ns flight.routes.core
  (:require
    [flight.util.schema]
    [flight.routes.restricted :refer [user-routes vendor-routes mod-routes admin-routes]]
    [flight.routes.public :refer [public-routes]]
    [flight.layout :refer [error-page]]
    [clojure.tools.logging :as log]
    [io.aviso.exception :as e]
    [compojure.api.sweet :refer :all]))

(def frame-rules
  (concat
    e/*default-frame-rules*
    [[:package #"java\.lang\.*" :hide]
     [:package #"java\.util\.concurrent.*" :hide]
     [:package #"io\.undertow.*" :hide]
     [:package #"immutant\.web.*" :hide]
     [:package #"ring\.middleware.*" :hide]
     [:package #"org\.projectodd.*" :hide]
     [:package #"compojure.*" :hide]
     [:package #"buddy.*" :hide]
     [:package #"muuntaja.*" :hide]
     [:package #"clojure.*" :omit]
     [:package #"prone.*" :omit]
     [:package #"selmer.*" :omit]
     [:package #"ring.swagger.*" :omit]]
    ))

(defn exception-handler [exception]
  (log/error (.getMessage exception))
  (binding [e/*default-frame-rules* frame-rules
            e/*traditional* true]
      (e/write-exception exception))
  (error-page {:status 400 :title "invalid request"} "error/rich.html"))

(defapi core-routes
  {:swagger {:spec "/swagger.json"
             :ui "/api-docs"}

   :exceptions
  {:handlers
  {
    java.sql.SQLException exception-handler
    ::compojure.api.exception/request-parsing exception-handler
    ::compojure.api.exception/request-validation exception-handler
    ::compojure.api.exception/default exception-handler
  }}}
  public-routes
  vendor-routes
  mod-routes
  admin-routes
  user-routes)
