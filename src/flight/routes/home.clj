(ns flight.routes.home
  (:require [flight.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [flight.util.session :as session]
            [flight.routes.helpers :refer :all]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]))

(defn home-page []
  (ok {}))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))
