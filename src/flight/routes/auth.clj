(ns flight.routes.auth
  (:require
   [compojure.api.sweet :refer :all]
   [flight.layout :as layout]
   [flight.models.message :as message]
   [flight.models.order :as order]
   [flight.models.user :as users]
   [flight.routes.helpers :refer :all]
   [flight.util.pgp :as pgp]
   [flight.util.session :as session]
   [schema.core :as s]))

(defn login-page
  ([referer]
   (when referer (session/flash-put :referer referer))
   (layout/render "login.html" (set-info))))

(defroutes* auth-routes
  (context*
   "/login" []
   (GET* "/" {{referer "referer"} :headers} (login-page referer))))
