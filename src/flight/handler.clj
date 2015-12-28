(ns flight.handler
  (:use [flight.util.schema])
  (:require [compojure.core :refer [defroutes routes wrap-routes]]
            [compojure.route :as route]
            [flight.cache :as cache]
            [flight.config :refer [defaults]]
            [flight.db.core :as db]
            [flight.env :refer [env]]
            [flight.layout :refer [error-page]]
            [flight.middleware :as middleware]
            [flight.routes.core :refer [core-routes]]
            [flight.routes.home :refer [home-routes]]
            [mount.core :as mount]
            [selmer.parser :as parser]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]))

(defroutes base-routes
  (route/not-found "Not Found"))

(defn log-path []
  (if (env :dev)
    (env :log-path)
    (str (System/getProperty "user.home") (env :log-path))))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (log/merge-config!
    {:level     ((fnil keyword :info) (env :log-level))
     :appenders {:rotor (rotor/rotor-appender
                          {:path (log-path)
                           :max-size (* 512 1024)
                           :backlog 10})}})

  (log/info "logging at" (log-path))

  (mount/start)
  ((:init defaults)))

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (log/info "flight is shutting down...")
  (mount/stop)
  (log/info "shutdown complete!"))

(def app-routes
  (routes
    (wrap-routes #'home-routes middleware/wrap-csrf)
    (wrap-routes #'core-routes middleware/wrap-csrf)
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))

(def app (middleware/wrap-base #'app-routes))
