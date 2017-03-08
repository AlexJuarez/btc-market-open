(ns flight.routes.restricted
  (:require
    [compojure.api.sweet :refer :all]
    [flight.routes.vendor.sales :as sales]
    [flight.routes.vendor.images :as images]
    [flight.routes.vendor.news :as news]
    [flight.routes.vendor.postage :as postage]
    [flight.routes.account :as account]
    [flight.routes.cart :as cart]
    [flight.routes.listings :as listings]
    [flight.routes.market :as market]
    [flight.routes.message :as message]
    [flight.routes.moderator :as moderator]
    [flight.routes.orders :as orders]
    [ring.util.response :as resp]
    [flight.access :refer [wrap-restricted]]
    [flight.access :as access]))

(defn login-redirect [_ _]
  (resp/redirect "/login"))

(defn home-redirect [_ _]
  (resp/redirect "/"))

(def user-authenticated
  {:rule access/authenticated? :on-error login-redirect})

(def mod-authenticated
  {:rule access/moderator? :on-error home-redirect})

(def admin-authenticated
  {:rule access/admin? :on-error home-redirect})

(defroutes vendor-routes
  (context
    "/vendor" []
    :access-rule user-authenticated
    sales/vendor-routes
    postage/vendor-routes
    news/vendor-routes
    images/vendor-routes
    listings/vendor-routes))

(defroutes user-routes
  account/user-routes
  cart/user-routes
  listings/user-routes
  market/user-routes
  message/user-routes
  orders/user-routes)

(defroutes mod-routes
  (context
    "/moderator" []
    :access-rule mod-authenticated
    market/mod-routes
    moderator/mod-routes))

(defroutes admin-routes
  (context
    "/admin" []
    :access-rule admin-authenticated
    moderator/admin-routes
    ))
