(ns flight.routes.core
  (:require
    [flight.routes.auth :refer [auth-routes]]
    [flight.routes.market :refer [market-routes]]
    [flight.routes.message :refer [message-routes]]
    [flight.routes.orders :refer [order-routes]]
    [flight.routes.account :refer [account-routes]]
    [flight.routes.sales :refer [sales-routes]]
    [flight.middleware :as middleware]
    [compojure.api.sweet :refer :all]))

(defapi core-routes
  {:format {:formats [:json-kw]}}
  auth-routes
  market-routes
  message-routes
  order-routes
  account-routes
  sales-routes)
