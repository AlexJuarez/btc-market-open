(ns flight.routes.core
  (:require
    [flight.util.schema]
    [flight.routes.restricted :refer [user-routes vendor-routes mod-routes admin-routes]]
    [flight.routes.public :refer [public-routes]]
    [compojure.api.sweet :refer :all]))

(defapi core-routes
  public-routes
  user-routes
  vendor-routes
  mod-routes
  admin-routes)
