(ns flight.routes.vendor.images
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.models.image :as image]
    [flight.util.core :as util :refer [user-id]]
    [flight.layout :as layout :refer [error-page]]
    [flight.util.session :as session]
    [ring.util.response :as resp]))


(defn images-page []
  (let [images (image/all (user-id)) success (session/flash-get :success)]
    (layout/render "images/index.html" {:images images :success success})))

(defn images-upload
  ([]
    (layout/render "images/upload.html"))
  ([{image :image}]
   (parse-image nil image)
   (session/flash-put! :success "image uploaded")
   (resp/redirect "/vendor/images")))

(defn image-delete [id]
  (image/remove! id (user-id))
  (session/flash-put! :success "image deleted")
  (resp/redirect "/vendor/images"))

(defn images-edit
  ([]
    (let [images (image/all (user-id))]
      (layout/render "images/index.html" {:images images :edit true})))
  ([{:keys [name] :as slug}]
    (dorun (map #(image/update! (key %) {:name (val %)}) name))
    (images-edit)))

(defroutes vendor-routes
  (context
      "/images" []
      (GET "/" [] (images-page))
      (POST "/edit" {params :params} (images-edit params))
      (GET "/edit" [] (images-edit))
      (POST "/upload" {params :params} (images-upload params))
      (GET "/upload" [] (images-upload)))
    (context
      "/image/:id" []
      :path-params [id :- Long]
      (GET "/delete" [] (image-delete id))))
