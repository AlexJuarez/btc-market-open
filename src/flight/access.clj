(ns flight.access
  (:require
    [ring.util.http-response :refer [unauthorized content-type]]
    [ring.util.response :as resp]
    [flight.util.user :as user]
    [cheshire.core :refer [encode]]
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

(defn redirect [req path]
  (let [media-type (get-in req [:headers "accept"])]
    (if (= media-type "application/json")
        (content-type (unauthorized (encode {:error "not authorized" :redirect path})) "application/json")
        (resp/redirect path)
        )))

(defn login-redirect [req _]
  (redirect req "/login"))

(defn home-redirect [req _]
  (redirect req "/"))

(def user-authenticated
  {:handler authenticated? :on-error login-redirect})

(def mod-authenticated
  {:handler moderator? :on-error home-redirect})

(def admin-authenticated
  {:handler admin? :on-error home-redirect})
