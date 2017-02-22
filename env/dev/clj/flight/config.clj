(ns flight.config
  (:require [selmer.parser :as parser]
            [taoensso.timbre :as timbre]
            [flight.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (timbre/info "-=[flight started successfully using the development profile]=-"))
   :middleware wrap-dev})
