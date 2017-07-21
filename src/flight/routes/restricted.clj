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
    [flight.routes.admin :as admin]
    [ring.util.response :as resp]
    [ring.util.http-response :refer [unauthorized content-type]]
    [flight.env :refer [env]]
    [cheshire.core :refer [encode]]
    [flight.access :as access]))

(defn redirect [req path]
  (let [media-type (get-in req [:headers "accept"])]
    (if (= media-type "application/json")
        (content-type (unauthorized (encode {:error "not authorized" :redirect path})) "application/json")
        (resp/redirect path)
        )))

(defn login-redirect [req _]
  (redirect req "/login"))

(defn home-redirect [req _]
  (redirect req "/"))

(def user-authenticated
  {:handler access/authenticated? :on-error login-redirect})

(def mod-authenticated
  {:handler access/moderator? :on-error home-redirect})

(def admin-authenticated
  {:handler access/admin? :on-error home-redirect})

(defroutes vendor-routes
  (context
    "/vendor" []
    :tags        ["vendor"]
    :access-rule user-authenticated
    sales/vendor-routes
    postage/vendor-routes
    news/vendor-routes
    images/vendor-routes
    listings/vendor-routes))

(defroutes user-routes
  (context "" []
           :tags ["user"]
           :access-rule user-authenticated
           account/user-routes
           cart/user-routes
           listings/user-routes
           market/user-routes
           message/user-routes
           orders/user-routes))

(defroutes mod-routes
  (context
    "/moderator" []
    :tags        ["moderator"]
    :access-rule mod-authenticated
    market/mod-routes
    moderator/mod-routes))

(defroutes admin-routes
  (context
    "/admin" []
    :tags        ["admin"]
    :access-rule admin-authenticated
    admin/admin-routes
    moderator/admin-routes))
