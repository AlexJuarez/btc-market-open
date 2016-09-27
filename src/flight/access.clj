(ns flight.access
  (:require [flight.util.user :as user]))

(defn authenticated? [request]
  (not (nil? (:id (user/current)))))

(defn moderator? [request]
  (= true (:mod (user/current))))

(defn admin? [reqest]
  (= true (:admin (user/current))))
