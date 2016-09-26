(ns flight.routes.moderator
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout]
    [flight.models.order :as order]
    [flight.models.review :as review]
    [flight.util.hashids :as hashids :refer [Hashid]]
    [flight.models.moderate :as moderate]
    [flight.models.resolution :as resolution]
    [flight.models.feedback :as feedback]
    [flight.models.message :as messages]
    [flight.models.user :as user]
    [flight.util.core :as util :refer [user-id]]
    [ring.util.response :as resp]
))

(def per-page 25)

(defn encrypt-ticket-ids [tickets]
  (map #(assoc % :id (hashids/encrypt-ticket-id (:id %))) tickets))

(defn moderator-page [page]
  (let [orders (-> (order/moderate page per-page) encrypt-ids)
        pagemax (util/page-max 10 per-page)
        support (encrypt-ticket-ids (feedback/all))]
  (layout/render "moderate/index.html" {:orders orders
                                        :tickets support
                                        })))

(defn support-view
  ([raw-id]
   (let [id (hashids/decrypt-ticket-id raw-id)
         ticket (feedback/get id)
         messages (messages/all id)]
     (layout/render "moderate/support.html" {:ticket ticket :messages messages :id raw-id})))
  ([raw-id slug]
   (let [id (hashids/decrypt-ticket-id raw-id)
         ticket (feedback/get id)]
     (feedback/add-response! id slug (user-id))
     (layout/render "moderate/support.html" {:ticket ticket :messages (messages/all id) :id raw-id}))))

(defn est [resolutions total]
  (map #(assoc % :est (* (/ (:percent %) 100) total)
                 :voted (moderate/voted? (:id %) (user-id)))
       resolutions))

(defn moderator-view [id & errors]
  (let [order (-> (order/moderate-order id) encrypt-id convert-order-price)
        past-orders (order/count-past (:user_id order))
        seller (user/get (:seller_id order))
        seller-resolutions (-> (order/past-seller-resolutions (:seller_id order)) encrypt-ids)
        buyer (user/get (:user_id order))
        buyer-resolutions (-> (order/past-resolutions (:user_id order)) encrypt-ids)
        resolutions (resolution/all id)
        modresolutions (moderate/all id)]
    (layout/render "moderate/resolution.html" {:resolutions (estimate-refund resolutions order)
                                               :modresolutions (est modresolutions (:total order))
                                               :buyer buyer
                                               :errors (first errors)
                                               :seller-rating (int (* (/ (or (:rating seller) 0) 5.0) 100))
                                               :buyer-resolutions buyer-resolutions
                                               :seller-resolutions seller-resolutions
                                               :seller seller :past_orders past-orders})))

(defn moderator-add-vote [id res]
    (moderate/vote! res (user-id))
    (resp/redirect (str "/moderate/" id)))

(defn moderator-remove-vote [id res]
    (moderate/remove-vote! res (user-id))
    (resp/redirect (str "/moderate/" id)))

(defn apply-resolution [id res]
  (moderate/accept! res (user-id))
  (resp/redirect (str "/moderate/" id)))

(defn moderator-add-resolution [raw_id slug]
  (let [id (hashids/decrypt raw_id)
        res (moderate/add! id slug (user-id))]
    (moderator-view raw_id res)))

(defroutes* moderator-routes
  (context*
    "/moderate" []
    (GET* "/" []
          :query-params [{page :- Long 1}] (moderator-page page))
    (context*
      "/:id" []
      :path-params [id :- Hashid]
      (GET* "/" [] (moderator-view id))
      (POST* "/" {params :params} (moderator-add-resolution id params)))

    (context*
      "/support/:id" []
      :path-params [id :- String]
      (GET* "/" [] (support-view id))
      (POST* "/" {params :params} (support-view id params)))
    (context* "/:id/:res" []
              :path-params [id :- Long
                            res :- Long]
              (GET* "/upvote" [id res] (moderator-add-vote id res))
              (GET* "/downvote" [id res] (moderator-remove-vote id res))))

  (GET* "/admin/:id/:res/resolve" [id res] (apply-resolution id res)))
