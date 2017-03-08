(ns flight.routes.public
  (:require
    [compojure.api.sweet :refer :all]
    [flight.routes.market :as market]
    [flight.routes.cart :as cart]
    [flight.routes.auth :as auth]))

(defroutes public-routes
  market/public-routes
  cart/public-routes
  auth/public-routes)
