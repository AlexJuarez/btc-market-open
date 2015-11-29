(ns flight.config
  (:require [taoensso.timbre :as timbre]))

(def defaults
  {:init
   (fn []
     (timbre/info "\n-=[flight started successfully]=-"))
   :middleware identity})
