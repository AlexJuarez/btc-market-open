(ns flight.routes.core
  (:require
    [flight.routes.auth :refer [auth-routes]]
    [flight.routes.market :refer [market-routes]]
    [flight.routes.message :refer [message-routes]]
    [flight.routes.orders :refer [order-routes]]
    [flight.routes.account :refer [account-routes]]
    [flight.routes.sales :refer [sales-routes]]
    [flight.routes.listings :refer [listing-routes]]
    [flight.routes.postage :refer [postage-routes]]
    [flight.routes.cart :refer [cart-routes]]
    [flight.routes.moderator :refer [moderator-routes]]
    [flight.middleware :as middleware]
    [compojure.api.sweet :refer :all]))

(defapi core-routes
  {:exceptions nil}
  auth-routes
  market-routes
  message-routes
  order-routes
  account-routes
  sales-routes
  listing-routes
  postage-routes
  cart-routes
  moderator-routes)
