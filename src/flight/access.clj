(ns flight.access
  (:require
    [flight.util.user :as user]
    [buddy.auth.accessrules :refer [restrict]]))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler  (:handler rule)
                     :on-error (:on-error rule)}))

(defn authenticated? [request]
  (not (nil? (:id (user/current)))))

(defn moderator? [request]
  (= true (:mod (user/current))))

(defn admin? [reqest]
  (= true (:admin (user/current))))
