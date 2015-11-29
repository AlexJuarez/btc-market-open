(ns flight.handler
  (:require [compojure.core :refer [defroutes routes wrap-routes]]
            [flight.layout :refer [error-page]]
            [flight.routes.home :refer [home-routes]]
            [flight.middleware :as middleware]
            [flight.db.core :as db]
            [compojure.route :as route]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [selmer.parser :as parser]
            [environ.core :refer [env]]
            [flight.config :refer [defaults]]
            [mount.core :as mount]))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (timbre/merge-config!
    {:level     ((fnil keyword :info) (env :log-level))
     :appenders {:rotor (rotor/rotor-appender
                          {:path (or (env :log-path) "flight.log")
                           :max-size (* 512 1024)
                           :backlog 10})}})
  (mount/start)
  ((:init defaults)))

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "flight is shutting down...")
  (mount/stop)
  (timbre/info "shutdown complete!"))

(def app-routes
  (routes
    (wrap-routes #'home-routes middleware/wrap-csrf)
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))

(def app (middleware/wrap-base #'app-routes))
