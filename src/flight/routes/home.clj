(ns flight.routes.home
  (:require [flight.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [flight.util.session :as session]
            [flight.routes.helpers :refer :all]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/about" [] (about-page)))
