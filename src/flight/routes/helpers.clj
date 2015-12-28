(ns flight.routes.helpers
  (:require
   [flight.util.session :as session]))

(defn is-user-logged-in? []
  (and
   (not (nil? (session/get :user_id)))
   (session/get :authed)))
