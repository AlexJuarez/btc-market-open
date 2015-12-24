(ns flight.routes.core
  (:require
   [flight.routes.auth :refer auth-routes]
   [compojure.api.sweet :refer :all]))

(defroutes* core-routes
  auth-routes)
