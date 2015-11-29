(ns flight.env
  (:require [environ.core :as environ]))

(defonce ^{:doc "A map of enviroment variables including external configuration"}
  env
  environ/env)
