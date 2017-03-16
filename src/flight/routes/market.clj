(ns flight.routes.market
  (:require
   [compojure.api.sweet :refer :all]
   [flight.cache :as cache]
   [flight.env :refer [env]]
   [flight.layout :as layout]
   [flight.models.bookmark :as bookmark]
   [flight.models.category :as category]
   [flight.models.fan :as follower]
   [flight.models.feedback :as feedback]
   [flight.models.listing :as listing]
   [flight.models.post :as post]
   [flight.models.region :as regions]
   [flight.models.report :as report]
   [flight.models.resolution :as resolution]
   [flight.models.review :as review]
   [flight.models.user :as user]
   [flight.routes.helpers :refer :all]
   [flight.util.core :as util :refer [user-id]]
   [flight.util.image :as image]
   [flight.util.markdown :as md]
   [flight.util.session :as session]
   [ring.util.http-response :refer :all]
   [ring.util.response :as resp]
   [schema.core :as s]))

(def per-page 10)

(def user-listings-per-page 10)

(def listings-per-page 20)

(def sort-options ["lowest" "highest" "title" "newest"])

(defn market-page [url {:keys [cid page sort_by ships_to ships_from] :as params}]
  (let [cid (or cid 1)
        page (or page 1)
        categories (category/public cid)
        pagemax (util/page-max (:count categories) listings-per-page)
        params (into {} (filter (comp identity second) {:sort_by sort_by :ships_to ships_to :ships_from ships_from}))
        listings (listing/public cid page listings-per-page params)]
    (layout/render "market/index.html"
                   {:paginate {:page page :max pagemax :url url :params params}
                    :listings listings
                    :categories {:tree categories :params params :id cid}}
                   params)))

(defn home-page [params]
  (market-page "/" params))

(defn category-page [id params]
  (market-page (str "/category/" id) (assoc params :cid id)))

(defn user-key [id]
  (let [user (user/get id)]
    (-> (resp/response (:pub_key user))
        (resp/content-type "text/plain")
        (resp/header "Content-Disposition" (str "attachment;filename=" (:pub_key_id user) ".asc")))))

(defn user-view [id page]
  (let [user (user/get id)
        id (:id user)
        page (or page 1)
        description (md/md->html (:description user))
        listings (:listings user)
        pagemax (util/page-max listings user-listings-per-page)]
    (layout/render "users/view.html" user {:paginate {:page page :max pagemax :url (str "/user/" id)}
                                           :listings-all (listing/public-for-user id page user-listings-per-page)
                                           :description description
                                           :posts (post/get-updates id)
                                           :feedback-rating (int (* (/ (:rating user) 5) 100))
                                           :review (review/for-seller id)
                                           :reported (report/reported? id (user-id) "user")
                                           :followed (follower/exists? id (user-id))})))

(defn search-page [query cid]
  (if (and (>= (count query) 3) (<= (count query) 100))
    (let [q (str "%" query "%")
          cid (or cid 1)
          users (user/search q)
          listings (listing/search q)
          categories (category/public cid q)
          category-results (category/search q)
          message (if (and (empty? users) (empty? listings) (empty? categories)) "Nothing was found for your query. Please try again.")]
      (layout/render "market/search.html" {:users users :listings listings :categories {:tree categories :id cid} :category-results category-results :query query :message message}))
    (layout/render "market/search.html" {:message "Your query is too short it needs to be longers than three characters and less than 100."} )))

(defn support-page
  ([]
   (layout/render "support.html"))
  ([slug]
   (let [post (feedback/add! slug (user-id))])
   (layout/render "support.html" {:message "Your request for support has been recieved."})))

(defn api-vendors [api_key]
  (map #(assoc % :uri (str "/user/" (:alias %))) (user/vendor-list)))

(defn format-listing [listing regions]
  (dissoc
   (assoc listing
     :item_link (str (env :domain) (:id listing))
     :ship_from (regions (:from listing))
     :image_encstr (image/image-data (:image_id listing) "_max")
     :item_rating (int (* 100 (/ (:rating listing) 5)))
     :vendor_link (str "/user/" (:id listing))
     :vendor_rating (int (* 100 (/ (:vendor_rating listing) 5)))
     :ship_to (map #(regions %) (:to listing))
     :item_create_time (.getTime (:item_create_time listing))
     :item_update_time (.getTime (:item_update_time listing))
     :price (util/convert-price (:price listing) (:currency_id listing) 1)
     )
   :to
   :from
   :id
   :category_id
   :currency_id))

(defn format-for-grams [listings]
  (let [regions (cache/cache! "regions_map" (into {} (map #(vector (:id %) (:name %)) (regions/all))))]
    (map #(format-listing % regions) listings)))

(defn api-listings [params sign]
  (let [page (or (:start params) 1)
        per-page (or (:count params) 500)]
    {:items (format-for-grams (listing/all page per-page))
     :start page
     :pagecount per-page
     :totalcount (listing/count)}))

(defn listing-view [id page]
  (let [listing (listing/view id)
        categories (category/public (:category_id listing))
        page (or page 1)
        reviews (review/all id page per-page)
        revs (:reviews listing)
        description (md/md->html (:description listing))
        pagemax (util/page-max revs per-page)]
    (layout/render "listings/view.html" {:categories {:tree categories :id (:category_id listing)} :review reviews :page {:page page :max pagemax :url (str "/listing/" id)} :reported (report/reported? id (user-id) "listing") :bookmarked (bookmark/exists? id (user-id))} listing {:description description})))

(defn resolution-accept [id referer]
  (resolution/accept id (user-id))
  (resp/redirect referer))

(defroutes public-routes
  (context "" []
            :query-params [{page :- Long 1}
                           {sort_by :- (s/enum "lowest" "highest" "title" "newest" "bestselling") "bestselling"}
                           {ships_to :- Boolean false}
                           {ships_from :- Boolean false}]
            (GET "/" []
                  (home-page {:page page :sort_by sort_by :ships_to ships_to :ships_from ships_from}))
            (GET "/search" []
                  :query-params [q :- String
                                 {cid :- Long 1}]
                  (search-page q cid))
            (GET "/category/:id" []
                  :path-params [{id :- Long 1}]
                  (category-page id {:page page :sort_by sort_by :ships_to ships_to :ships_from ships_from})))
  (GET "/support" [] (support-page))

  (GET "/api/vendors" [api_key] (api-vendors api_key))
  (GET "/api/listings" {params :params {sign "sign"} :headers} (api-listings params sign))

  ;;public routes
  (context "/user/:id" []
           :path-params [id :- Long]
           :query-params [{page :- Long 1}]
           (GET "/" [] (user-view id page))
           (GET "/key" [] (user-key id)))
  (GET "/listing/:id" []
       :path-params [id :- Long]
       :query-params [{page :- Long 1}]
       (listing-view id page)))

;;restricted routes
(defroutes user-routes
  (POST "/support" {params :params} (support-page params))
  (context "/user/:id" []
           :path-params [id :- Long]
           (GET "/report" {{referer "referer"} :headers} (report-add id (user-id) "user" referer))
           (GET "/unreport" {{referer "referer"} :headers} (report-remove id (user-id) "user" referer))))

(defroutes mod-routes
  (GET "/resolution/:id/accept" {{referer "referer"} :headers}
       :path-params [id :- Long]
       (resolution-accept id referer)))
