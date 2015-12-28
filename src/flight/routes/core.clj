(ns flight.routes.core
  (:require
   [flight.routes.auth :refer [auth-routes]]
   [flight.middleware :as middleware]
   [compojure.api.sweet :refer :all]))

(defapi core-routes
  {:format {:formats [:json-kw]}}
  auth-routes)