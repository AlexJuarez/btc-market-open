(ns flight.routes.listings
  (:require
    [clojure.string :as string]
    [compojure.api.sweet :refer :all]
    [compojure.api.upload :as upload]
    [flight.layout :as layout]
    [flight.models.bookmark :as bookmark]
    [flight.models.category :as category]
    [flight.models.currency :as currency]
    [flight.models.image :as image]
    [flight.models.listing :as listing]
    [flight.models.postage :as postage]
    [flight.models.region :as region]
    [flight.util.message :as message]
    [flight.routes.helpers :refer :all]
    [flight.util.core :as util
     :refer               [user-id]]
    [flight.util.error :as error]
    [flight.util.session :as session]
    [ring.util.response :as resp]
    [schema.core :as s]))

(def per-page 10)

(defn listings-page [page]
  (let [listing-count (:listings (util/current-user))
        pagemax       (util/page-max listing-count per-page)]
    (layout/render "listings/index.html"
                   {:paginate {:page page :max pagemax}
                    :postages (postage/all (user-id))
                    :listings (listing/all (user-id) page per-page)})))

(defn listing-remove [id]
  (let [record (listing/remove! id (user-id))]
    (if (nil? record)
        (resp/redirect "/vendor/")
        (do
          (message/success! "listing removed")
          (resp/redirect "/vendor/listings")))))

(defn walk-current [lis c]
  (loop [l lis]
    (if (or (empty? l) (= (second (last l)) (:parent c)))
        (conj l [(:name c) (:id c)])
        (recur (pop l)))))

(defn create-categories [categories]
  (loop [cats    categories
         current []
         output  []]
    (if (empty? cats)
        output
        (let [c    (first cats)
              curr (if (empty? current) [[(:name c) (:id c)]] (walk-current current c))]
          (recur (rest cats) curr (conj output (assoc c :name (string/join " > " (map first curr)))))))))


(defn listing-create-page [&[listing params]]
  (layout/render "listings/create.html"
                 {:regions    (region/all)
                  :min-price  (util/convert-currency 1 0.01)
                  :recent     (listing/recent-shipping (user-id))
                  :images     (image/all (user-id))
                  :categories (create-categories (category/all))
                  :currencies (currency/all)}
                 params listing))

(defn listing-edit [id]
  (let [listing (listing/get id (user-id))]
    (listing-create-page listing {:edit true :id id})))

(defn listing-save [id slug]
  (let [listing (listing/update! slug id (user-id))]
    (message/success! "listing-updated")
    (listing-create-page listing {:edit true :id id})))

(defn listing-create
  "Listing creation page"
  ([]
    (listing-create-page))
  ([{:keys [image image_id] :as slug}]
    (let [listing (listing/add! slug (user-id))]
      (if (error/empty?)
          (do
            (message/success! "listing created")
            (resp/redirect (str "/vendor/listing/" (:id listing) "/edit")))
          (listing-create-page listing)))))

(defn listing-bookmark [id]
  (if-not (bookmark/exists? id (user-id))
    (bookmark/add! id (user-id)))
  (resp/redirect (str "/listing/" id)))

(defn listing-unbookmark [id referer]
  (bookmark/remove! id (user-id))
  (resp/redirect referer))

(s/defschema Listing
  {(s/optional-key :image_id) (s/both Long (s/pred #(image/exists? % (user-id)) 'exists?))
   (s/optional-key :public)   Boolean
   :title                     (Str 4 100)
   :price                     (s/both Double (in-range? 0))
   :currency_id               (s/both Long (s/pred currency/exists? 'exists?))
   :quantity                  (s/both Long (in-range? 0))
   :from                      (s/both Long (s/pred region/exists? 'exists?))
   :to                        [(s/both Long (s/pred region/exists? 'exists?))]
   :description               (Str 3000)
   :category_id               (s/both Long (s/pred category/exists? 'exists?))})

(defn update-listing-params [listing]
  (->
    (let [image_id (parse-image (listing "image_id") (listing "image"))]
      (if (and (not (number? image_id)) (string/blank? image_id))
          (assoc listing "image_id" nil)
          (assoc listing "image_id" (str image_id))))
    (assoc "public" (= (listing "public") "true"))
    (assoc "to" (or (listing "to[]") ["1"]))
    (dissoc "to[]")
    (dissoc "image")))

(defn middleware-listing [handler]
  (fn [request]
    (let [request (-> request
                      (update-in [:multipart-params] update-listing-params))]
      (handler request))))

(defroutes user-routes
  (context
    "/listing/:id" []
    :path-params [id :- Long]
    (GET "/bookmark" [] (listing-bookmark id))
    (GET "/unbookmark" {{referer "referer"} :headers} (listing-unbookmark id referer))
    (GET "/report" {{referer "referer"} :headers} (report-add id (user-id) "listing" referer))
    (GET "/unreport" {{referer "referer"} :headers} (report-remove id (user-id) "listing" referer))))

(defroutes vendor-routes
  (context
    "/listings" []
    (GET "/" []
         :query-params [{page :- Long 1}] (listings-page page))
    (GET "/create" [] (listing-create))
    (POST "/create" []
          :multipart-form [listing Listing]
          :middleware     [upload/wrap-multipart-params middleware-listing]
          (listing-create listing)))

  (context
    "/listing/:id" []
    :path-params [id :- Long]
    (GET "/edit" [] (listing-edit id))
    (GET "/remove" [] (listing-remove id))
    (POST "/edit" []
          :multipart-form [listing Listing]
          :middleware     [upload/wrap-multipart-params middleware-listing]
          (listing-save id listing))))
