(ns flight.routes.listings
  (:require
    [flight.util.session :as session]
    [compojure.api.sweet :refer :all]
    [compojure.api.upload :as upload]
    [flight.layout :as layout]
    [ring.util.response :as resp]
    [flight.models.listing :as listing]
    [flight.models.category :as category]
    [flight.models.bookmark :as bookmark]
    [flight.models.review :as review]
    [flight.models.region :as region]
    [flight.models.report :as report]
    [flight.routes.helpers :refer :all]
    [flight.models.image :as image]
    [clojure.string :as string]
    [flight.models.postage :as postage]
    [flight.models.currency :as currency]
    [flight.util.core :as util :refer [user-id]]
    [schema.core :as s]))

(def per-page 10)

(defn listings-page [page]
  (let [listing-count (:listings (util/current-user))
        pagemax (util/page-max listing-count per-page)]
    (layout/render "listings/index.html" {:paginate {:page page :max pagemax}
                                          :postages (postage/all (user-id))
                                          :listings (listing/all (user-id) page per-page)})))

(defn listing-remove [id]
  (let [record (listing/remove! id (user-id))]
  (if (nil? record)
    (resp/redirect "/vendor/")
  (do
    (session/flash-put! :success "listing removed")
    (resp/redirect "/vendor/listings")))))
;;Check convert currency set this to a global constant

(defn walk-current [lis c]
  (loop [l lis]
    (if (or (empty? l) (= (second (last l)) (:parent c)))
      (conj l [(:name c) (:id c)])
      (recur (pop l)))))

(defn create-categories [categories]
  (loop [cats categories
         current []
         output []]
    (if (empty? cats)
      output
      (let [c (first cats)
            curr (if (empty? current) [[(:name c) (:id c)]] (walk-current current c))]
        (recur (rest cats) curr (conj output (assoc c :name (string/join " > " (map first curr)))))))))

(defn listing-edit [id]
  (let [listing (listing/get id (user-id))
        success (session/flash-get :success)]
    (layout/render "listings/create.html" {:regions (region/all) :min-price (util/convert-currency 1 0.01)
                                           :edit true :success success :id id
                                           :recent (listing/recent-shipping (user-id))
                                           :images (image/all (user-id))
                                           :categories (create-categories (category/all))
                                           :currencies (currency/all)} listing)))

(defn listing-save [id {:keys [image image_id] :as slug}]
  (let [listing (listing/update! (assoc slug :image_id (parse-image image_id image)) id (user-id))]
    (layout/render "listings/create.html" listing
                   {:regions (region/all) :min-price (util/convert-currency 1 0.01)
                    :edit true :success "updated" :id id
                    :recent (listing/recent-shipping (user-id))
                    :images (image/all (user-id))
                    :categories (create-categories (category/all))
                    :currencies (currency/all)})))

(defn listing-create
  "Listing creation page"
  ([]
   (layout/render "listings/create.html" {:regions (region/all)
                                          :images (image/all (user-id))
                                          :recent (listing/recent-shipping (user-id))
                                          :categories (create-categories (category/all))
                                          :currencies (currency/all)}))
  ([{:keys [image image_id] :as slug}]
   (let [listing (listing/add! (assoc slug :image_id (parse-image image_id image)) (user-id))]
     (if (empty? (:errors listing))
      (do
        (session/flash-put! :success "listing created")
        (resp/redirect (str "/vendor/listing/" (:id listing) "/edit")))
      (layout/render "listings/create.html" {:regions (region/all)
                                             :images (image/all (user-id))
                                             :recent (listing/recent-shipping (user-id))
                                             :categories (create-categories (category/all))
                                             :currencies (currency/all)} listing)))))

(defn forms-page [page]
  (let []))

(defn listing-bookmark [id]
  (if-let [bookmark (:errors (bookmark/add! id (user-id)))]
    (session/flash-put! :bookmark bookmark))
  (resp/redirect (str "/listing/" id)))

(defn listing-unbookmark [id referer]
  (bookmark/remove! id (user-id))
  (resp/redirect referer))

(s/defschema Listing
  {(s/optional-key :image) upload/TempFileUpload
   (s/optional-key :image_id) Long
   (s/optional-key :public) Boolean
   (s/optional-key :hedged) Boolean
   :title String
   :price Double
   :curreny_id Long
   :quantity Long
   :from Long
   :to [Long]
   :description String
   })

(defroutes listing-routes
   (context
    "/listing/:id" []
     :path-params  [id :- Long]
     (GET "/bookmark" [] (listing-bookmark id))
     (GET "/unbookmark" {{referer "referer"} :headers} (listing-unbookmark id referer))
     (GET "/report" {{referer "referer"} :headers} (report-add id (user-id) "listing" referer))
     (GET "/unreport" {{referer "referer"} :headers} (report-remove id (user-id) "listing" referer)))

   (context
    "/vendor/forms" []
    (GET "/" []
          :query-params [{page :- Long 1}] (forms-page page)))

   (context
    "/vendor/listings" []
    (GET "/" []
          :query-params [{page :- Long 1}] (listings-page page))
    (GET "/create" [] (listing-create))
    (POST "/create" []
           :form [listing Listing] (listing-create listing)))

   (context
     "/vendor/listing/:id" []
     :path-params [id :- Long]
     (GET "/edit" [] (listing-edit id))
     (GET "/remove" [] (listing-remove id))
     (POST "/edit" []
            :form [listing Listing] (listing-save id listing))))
