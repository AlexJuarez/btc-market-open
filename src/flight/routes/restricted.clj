(ns flight.routes.restricted
  (:require
    [compojure.api.sweet :refer [defroutes context]]
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
    [flight.routes.admin :as admin]
    [flight.env :refer [env]]
    [flight.access :as access]))

(defroutes vendor-routes
  (context
    "/vendor" []
    :tags        ["vendor"]
    :access-rule access/user-authenticated
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
    :tags        ["moderator"]
    :access-rule access/mod-authenticated
    market/mod-routes
    moderator/mod-routes))

(defroutes admin-routes
  (context
    "/admin" []
    :tags        ["admin"]
    :access-rule access/admin-authenticated
    admin/admin-routes
    moderator/admin-routes))
