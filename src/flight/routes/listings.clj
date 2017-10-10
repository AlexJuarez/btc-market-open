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
    [flight.access :as access]
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

(defn- listing-params [& _]
  {:regions    (region/all)
   :min-price  (util/convert-currency 1 0.01)
   :recent     (listing/recent-shipping (user-id))
   :images     (image/all (user-id))
   :categories (create-categories (category/all))
   :currencies (currency/all)})

(defpage listing-edit-page
  :template ["listings/create.html"
             listing-params
             {:edit true :id (fn [id] id)}
             (fn [id] (listing/get id (user-id)))]
  :args [:id]
  :success "This listing has been updated."
  (fn [slug id]
    (listing/update! slug id (user-id))))

(defpage create-page
  :template ["listings/create.html" listing-params]
  :success "listing-created"
  (fn [slug]
    (let [listing (listing/add! slug (user-id))]
      (resp/redirect (str "/vendor/listing/" (:id listing) "/edit")))))

(defn listing-bookmark [id]
  (if-not (bookmark/exists? id (user-id))
    (bookmark/add! id (user-id)))
  (resp/redirect (str "/listing/" id)))

(defn listing-unbookmark [id referer]
  (bookmark/remove! id (user-id))
  (resp/redirect referer))

(s/defschema Listing
  {:image_id                      (s/maybe (s/both Long (s/pred #(image/exists? % (user-id)) 'exists?)))
   :public                        (s/maybe Boolean)
   :description                   (Str 0 3000)
   :title                         (Str 4 100)
   :price                         (s/both Double (in-range? 0))
   :currency_id                   (s/both Long (s/pred currency/exists? 'exists?))
   :quantity                      (s/both Long (in-range? 0))
   :from                          (s/both Long (s/pred region/exists? 'exists?))
   :to                            [(s/both Long (s/pred region/exists? 'exists?))]
   :category_id                   (s/both Long (s/pred category/exists? 'exists?))})

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
    :tags ["user"]
    :access-rule access/user-authenticated
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
    (GET "/create" [] (create-page))
    (POST "/create" []
          :multipart-form [listing Listing]
          :middleware     [upload/wrap-multipart-params middleware-listing]
          (create-page listing)))

  (context
    "/listing/:id" []
    :path-params [id :- Long]
    (GET "/edit" [] (listing-edit-page id))
    (GET "/remove" [] (listing-remove id))
    (POST "/edit" []
          :multipart-form [listing Listing]
          :middleware     [upload/wrap-multipart-params middleware-listing]
          (listing-edit-page listing id))))
