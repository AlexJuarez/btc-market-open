(ns flight.handler
  (:use [flight.util.schema])
  (:require [compojure.core :refer [defroutes routes wrap-routes]]
            [compojure.route :as route]
            [flight.config :refer [defaults]]
            [flight.layout :refer [error-page]]
            [flight.middleware :as middleware]
            [flight.routes.core :refer [core-routes]]
            [mount.core :refer [defstate] :as mount]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (wrap-routes #'core-routes middleware/wrap-csrf)))

(defn app [] (middleware/wrap-base #'app-routes))
