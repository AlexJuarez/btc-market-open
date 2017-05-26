(ns flight.routes.core
  (:require
    [flight.util.schema]
    [flight.routes.restricted :refer [user-routes vendor-routes mod-routes admin-routes]]
    [flight.routes.public :refer [public-routes]]
    [flight.layout :refer [error-page]]
    [compojure.api.sweet :refer :all]))

(defn exception-handler [exception]
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
