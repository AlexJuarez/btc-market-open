(ns flight.routes.postage
   (:require
     [flight.routes.helpers :refer :all]
     [compojure.api.sweet :refer :all]
     [flight.layout :as layout]
     [flight.models.currency :as currency]
     [flight.models.postage :as postage]
     [ring.util.response :as resp]
     [flight.util.core :refer [user-id]]
     [flight.util.session :as session]
     [schema.core :as s]
))

(s/defschema Postage
  {:title String
   :price Double
   :currency_id Long})

(defn postage-create
  ([]
   (layout/render "postage/create.html" {:currencies (currency/all)}))
  ([slug]
   (let [post (postage/add! slug (user-id))]
     (if (empty? (:errors post))
       (resp/redirect "/vendor/listings")
       (layout/render "postage/create.html" {:currencies (currency/all)}  post)))))

(defn postage-edit [id]
  (let [postage (postage/get id (user-id))]
    (layout/render "postage/create.html" {:currencies (currency/all)} postage)))

(defn postage-save [id slug]
  (let [post (postage/update! slug id (user-id))]
    (layout/render "postage/create.html" {:currencies (currency/all) :id id} post)))

(defn postage-remove [id]
  (let [record (postage/remove! id (user-id))]
  (if (nil? record)
    (resp/redirect "/vendor/listings")
  (do (session/flash-put! :success {:success "postage removed"})
    (resp/redirect "/vendor/listings")))))

(defroutes* postage-routes
  (context*
    "/vendor/postage" []
    (GET* "/create" [] (postage-create))
    (POST* "/create" []
           :form [postage Postage] (postage-create postage))
    (context* "/:id" []
              :path-params [id :- Long]
              (GET* "/edit" [] (postage-edit id))
              (POST* "/edit" []
                     :form [postage Postage] (postage-save id postage))
              (GET* "/remove" [] (postage-remove id)))))

