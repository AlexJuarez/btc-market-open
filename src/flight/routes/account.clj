(ns flight.routes.account
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout
     :refer            [error-page]]
    [flight.models.user :as user]
    [flight.models.fan :as follower]
    [flight.models.currency :as currency]
    [flight.models.review :as review]
    [flight.models.bookmark :as bookmark]
    [flight.models.region :as region]
    [ring.util.response :as resp]
    [flight.util.session :as session]
    [flight.util.error :as error]
    [flight.models.post :as post]
    [flight.util.markdown :as md]
    [flight.util.message :as message]
    [flight.util.user :refer [password-matches?]]
    [flight.util.core :as util
     :refer               [user-id current-user]]
    [flight.routes.account.pgp :refer [pgp-routes]]
    [flight.routes.account.wallet :refer [wallet-routes]]
    [schema.core :as s]
    [flight.access :as access]
    [ring.util.http-response :refer :all]))

(defonce reviews-per-page 25)

(defpage account-page
  :template
  ["account/index.html"
   {:alias (fn [& _] (:alias (current-user)))
    :currency_id (fn [& _] (:currency_id (current-user)))
    :region_id (fn [& _] (:region_id (current-user)))
    :regions (fn [& _] (region/all))
    :currencies (fn [& _] (currency/all))}]
  :success "Your account has been updated"
  (fn [slug] (user/update! (user-id) slug)))

(defn favorites-page []
  (let [bookmarks (map #(assoc % :price (util/convert-currency %)) (bookmark/all (user-id)))
        favs      (follower/all (user-id))]
    (layout/render "account/favorites.html" {:bookmarks bookmarks :favorites favs})))

(defn reviews-page
  ([page]
    (let [reviews (review/for-user (user-id) page reviews-per-page)
          pagemax (util/page-max (:reviewed (util/current-user)) reviews-per-page)]
      (layout/render "account/reviews.html"
                     {:reviews reviews
                      :paginate    {:page page :max pagemax :url "/account/reviews"}}))))

(defn review-edit
  ([id]
    (let [review (review/get id (user-id))]
      (layout/render "review/edit.html" review)))
  ([id slug]
    (review/update! id slug (user-id))
    (message/success! "review updated")
    (resp/redirect "/account/reviews")))

(defn password-validator [{:keys [newpass confirm]}]
  (when-not (= newpass confirm)
    (error/register! :newpass "password does not match confirmation")))

(defpage password-page
  :template ["account/password.html"]
  :success "You have successfully changed your password"
  :validator password-validator
  (fn [slug] (user/update-password! (user-id) slug)))

(defn user-follow [id]
  (if-let [follower (:errors (follower/add! id (user-id)))]
    (session/flash-put! :follower follower))
  (resp/redirect (str "/user/" id)))

(defn user-unfollow [id referer]
  (follower/remove! id (user-id))
  (resp/redirect referer))

(s/defschema Account
  {(s/optional-key :alias)       (Str 3 64 (is-alphanumeric?) (s/pred #(user/alias-availible? % (user-id)) 'availible?))
   (s/optional-key :currency_id) (s/both Long (s/pred currency/exists? 'exists?))
   (s/optional-key :region_id)   (s/both Long (s/pred region/exists? 'exists?))
   (s/optional-key :auth)        Boolean
   (s/optional-key :description) (Str 0 3000)})

(s/defschema Password
  {:password (Str 0 73 (s/pred password-matches? 'password-matches?))
   :newpass  (Str 8 73)
   :confirm  (Str 8 73)})

(s/defschema Review
  {:rating (s/both Long (in-range? 0 5))
   :shipped Boolean
   (s/optional-key :content) (Str 2000)})

(defn news-view [id]
  (let [article (post/get id)
        content (md/md->html (:content article))]
    (layout/render "news/view.html" article {:content content})))

(defroutes user-routes
  (context
    "/account" []
    :tags ["user"]
    :access-rule access/user-authenticated
    pgp-routes
    wallet-routes
    (page-route "/" account-page Account)
    (page-route "/password" password-page Password)
    (GET "/favorites" [] (favorites-page))
    (GET "/reviews" []
         :query-params [{page :- Long 1}] (reviews-page page)))

  (GET "/news/:id" []
       :tags ["user"]
       :access-rule access/user-authenticated
       :path-params [id :- Long]
       (news-view id))

  (context
    "/review/:id" []
    :tags ["user"]
    :access-rule access/user-authenticated
    :path-params [id :- Long]
    (GET "/edit" [] (review-edit id))
    (POST "/edit" []
          :form [review Review]
          (review-edit id review)))

  (context
    "/user/:id" []
    :tags ["user"]
    :access-rule access/user-authenticated
    :path-params [id :- Long]
    (GET "/follow" [] (user-follow id))
    (GET "/unfollow" {{referer "referer"} :headers} (user-unfollow id referer))))
