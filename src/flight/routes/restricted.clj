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
    [buddy.auth.accessrules :refer [restrict]]
    [flight.access :as access]))

(defn login-redirect [_ _]
  (resp/redirect "/login"))

(defn home-redirect [_ _]
  (resp/redirect "/"))

(def user-authenticated
  {:handler access/authenticated? :on-error login-redirect})

(def mod-authenticated
  {:handler access/moderator? :on-error home-redirect})

(def admin-authenticated
  {:handler access/admin? :on-error home-redirect})

(defroutes vendor-routes
  (context
    "/vendor" []
    :tags ["vendor"]
    :access-rule user-authenticated
    sales/vendor-routes
    postage/vendor-routes
    news/vendor-routes
    images/vendor-routes
    listings/vendor-routes))

(defroutes user-routes*
  (context "" []
    :tags ["user"]
    account/user-routes
    cart/user-routes
    listings/user-routes
    market/user-routes
    message/user-routes
    orders/user-routes))

(defroutes user-routes
  (restrict user-routes* user-authenticated))

(defroutes mod-routes
  (context
    "/moderator" []
    :tags ["moderator"]
    :access-rule mod-authenticated
    market/mod-routes
    moderator/mod-routes))

(defroutes admin-routes
  (context
    "/admin" []
    :tags ["admin"]
    :access-rule admin-authenticated
    moderator/admin-routes
    ))
