(ns flight.routes.vendor.postage
   (:require
     [flight.routes.helpers :refer :all]
     [compojure.api.sweet :refer :all]
     [flight.layout :as layout]
     [flight.models.currency :as currency]
     [flight.models.postage :as postage]
     [ring.util.response :as resp]
     [flight.util.core :refer [user-id]]
     [flight.util.session :as session]
     [flight.util.error :as error]
     [schema.core :as s]))

(s/defschema Postage
  {:title (s/both String (in-range? 4 100))
   :price (s/both Double (in-range? 0))
   :currency_id (s/both Long (s/pred currency/exists? 'exists?))})

(defn postage-create
  ([]
   (layout/render "postage/create.html" {:currencies (currency/all)}))
  ([slug]
   (let [post (postage/add! slug (user-id))]
     (if (error/empty?)
       (resp/redirect "/vendor/listings")
       (layout/render "postage/create.html" {:currencies (currency/all)}  post)))))

(defn postage-edit [id]
  (let [post (postage/get id (user-id))]
    (layout/render "postage/edit.html" {:currencies (currency/all)} post)))

(defn postage-save [id slug]
  (let [post (postage/update! slug id (user-id))]
    (layout/render "postage/edit.html" {:currencies (currency/all) :id id} post)))

(defn postage-remove [id]
  (let [record (postage/remove! id (user-id))]
  (if (nil? record)
    (resp/redirect "/vendor/listings")
  (do (session/flash-put! :success {:success "postage removed"})
    (resp/redirect "/vendor/listings")))))

(defroutes vendor-routes
  (context
    "/postage" []
    (GET "/create" [] (postage-create))
    (POST "/create" []
           :form [postage Postage] (postage-create postage))
    (context "/:id" []
              :path-params [id :- Long]
              (GET "/edit" [] (postage-edit id))
              (POST "/edit" []
                     :form [postage Postage] (postage-save id postage))
              (GET "/remove" [] (postage-remove id)))))

