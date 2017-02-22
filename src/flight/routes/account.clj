(ns flight.routes.account
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [compojure.api.meta :refer [restructure-param]]
    [buddy.auth.accessrules :refer [restrict]]
    [flight.layout :as layout :refer [error-page]]
    [flight.models.user :as user]
    [flight.models.fan :as follower]
    [flight.models.audit :as audit]
    [flight.models.currency :as currency]
    [flight.models.review :as review]
    [flight.models.bookmark :as bookmark]
    [flight.models.post :as post]
    [flight.models.image :as image]
    [flight.models.region :as region]
    [ring.util.response :as resp]
    [flight.util.session :as session]
    [flight.util.core :as util :refer [user-id]]
    [flight.routes.account.pgp :refer [pgp-routes]]
    [flight.util.markdown :as md]
    [schema.core :as s]
    [flight.access :as access]
    [ring.util.http-response :refer :all]
    ))

(defonce reviews-per-page 25)

(defn account-page []
  (layout/render "account/index.html" {:regions (region/all) :currencies (currency/all)}))

(defn account-update [slug]
  (let [user (user/update! (user-id) slug)]
    (account-page)))

(defn withdrawal [{:keys [amount address pin] :as slug}]
  (let [errors (:errors (user/withdraw-btc! slug (user-id)))
        user (util/current-user)
        transactions (audit/all (user-id))]
    (layout/render "account/wallet.html" {:amount amount :address address
                                          :errors errors :transactions transactions
                                          :balance (not (= (:currency_id user) 1))})))
(defn change-pin [slug]
  (let [errors (:errors (user/update-pin! (user-id) slug))
        user (util/current-user)
        transactions (audit/all (user-id))]
    (layout/render "account/wallet.html"
                   (if (empty? errors) {:pin-success "Your pin has been changed"})
                   {:pinerrors errors :transactions transactions
                    :balance (not (= (:currency_id user) 1))})))

(defn wallet-page
  ([]
   (let [user (util/current-user)
         transactions (audit/all (user-id))]
     (layout/render "account/wallet.html" {:transactions transactions :balance (not (= (:currency_id user) 1))}))
   )
  ([slug]
   (if (not (nil? (:confirmpin slug)))
     (change-pin slug)
     (withdrawal slug))))

(defn wallet-new []
  (user/update-btc-address! (user-id))
  (resp/redirect "/account/wallet"))

(defn favorites-page []
  (let [bookmarks (map #(assoc % :price (util/convert-currency %)) (bookmark/all (user-id)))
        favs (follower/all (user-id))]
    (layout/render "account/favorites.html" {:bookmarks bookmarks :favorites favs})))

(defn reviews-page
  ([page]
   (let [reviews (review/for-user (user-id) page reviews-per-page)
         success (session/flash-get :success)
         pagemax (util/page-max (:reviewed (util/current-user)) reviews-per-page)]
     (layout/render "account/reviews.html" {:success success :reviews reviews :page {:page page :max pagemax :url "/account/reviews"}}))))

(defn review-edit
  ([id]
   (let [review (review/get id (user-id))]
     (layout/render "review/edit.html" review)))
  ([id slug]
   (review/update! id slug (user-id))
   (session/flash-put! :success "review updated")
   (resp/redirect "/account/reviews")))

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

(defn password-page
  ([]
    (layout/render "account/password.html"))
  ([slug]
   (let [errors (user/update-password! (user-id) slug)
         message (if (empty? errors) "You have successfully changed your password")]
    (layout/render "account/password.html" {:message message :errors errors}))))

(defn images-edit
  ([]
    (let [images (image/all (user-id))]
      (layout/render "images/edit.html"{:images images})))
  ([{:keys [name] :as slug}]
    (dorun (map #(image/update! (key %) {:name (val %)}) name))
    (images-edit)))

(defn user-follow [id]
  (if-let [follower (:errors (follower/add! id (user-id)))]
    (session/flash-put! :follower follower))
  (resp/redirect (str "/user/" id)))

(defn user-unfollow [id referer]
  (follower/remove! id (user-id))
  (resp/redirect referer))

(defn news-page []
  (let [posts (post/all (user-id))]
      (layout/render "news/index.html" {:posts posts})))

(defn news-create
  ([]
   (layout/render "news/create.html"))
  ([slug]
   (let [post (post/add! slug (user-id))]
     (if (empty? (:errors post))
       (resp/redirect (str "/vendor/news/" (:id post) "/edit"))
       (layout/render "news/create.html" post)))))

(defn news-publish [id]
  (post/publish! id (user-id))
  (resp/redirect "/vendor/news"))

(defn news-delete [id]
  (post/remove! id (user-id))
  (resp/redirect "/vendor/news"))

(defn news-view [id]
  (let [article (post/get id)
        content (md/md->html (:content article))]
    (layout/render "news/view.html" article {:content content})
  ))

(defn news-edit
  ([id]
   (let [ article (post/get id)
         content (md/md->html (:content article))]
     (layout/render "news/create.html" article {:preview content})))
  ([id slug]
   (let [article (post/update! slug (user-id))
         content (md/md->html (:content article))]
     (layout/render "news/create.html" article {:preview content}))))


(s/defschema Account
  {(s/optional-key :alias) (s/both String (in-range? 3 64) (is-alphanumeric?) (s/pred user/alias-availible? 'user/alias-availible?))
   (s/optional-key :currency_id) Long
   (s/optional-key :region_id) Long
   (s/optional-key :auth) Boolean
   (s/optional-key :description) String})

(s/defschema Password
  {:password String
   :newpass String
   :confirm String})

(s/defschema Wallet
  {(s/optional-key :pin) String
   (s/optional-key :confirmpin) String
   (s/optional-key :oldpin) String
   (s/optional-key :amount) Double
   (s/optional-key :address) String})

(s/defschema Article
  {(s/optional-key :subject) String
   (s/optional-key :public) Boolean
   (s/optional-key :published) Boolean
   (s/optional-key :content) String})

(s/defschema Review
  {:rating (s/enum :1 :2 :3 :4 :5)
   :shipped Boolean
   :content String})

(defn access-error [_ _]
  (resp/redirect "/"))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler  rule
                     :on-error access-error}))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-restricted rule]))

(defroutes account-routes
  (context
    "/account" []
    :auth-rules access/authenticated?
    pgp-routes
    (GET "/" [] (account-page))
    (POST "/" []
           :form [info Account]
           (account-update info))

    (GET "/password" [] (password-page))
    (POST "/password" []
           :form [update Password] (password-page update))
    (GET "/wallet" [] (wallet-page))
    (POST "/wallet" []
           :form [slug Wallet]
           (wallet-page slug))
    (GET "/wallet/new" [] (wallet-new))
    (GET "/favorites" [] (favorites-page))
    (GET "/reviews" []
          :query-params [{page :- Long 1}] (reviews-page page)))

  (GET "/news/:id" []
        :path-params [id :- Long]
        (news-view id))

  (context
    "/vendor" []
    :auth-rules access/authenticated?
    (context
      "/news" []
      (GET "/" [] (news-page))
      (GET "/create" [] (news-create))
      (POST "/create" []
             :form [article Article]
             (news-create article))
      (context
        "/:id" []
        :path-params [id :- Long]
        (GET "/edit" [] (news-edit id))
        (POST "/edit" []
               :form [article Article]
               (news-edit id article))
        (GET "/publish" [] (news-publish id))
        (GET "/delete" [] (news-delete id))))
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

   (context
    "/review/:id" []
     :auth-rules access/authenticated?
     :path-params [id :- Long]
     (GET "/edit" [] (review-edit id))
     (POST "/edit" []
            :form [review Review]
            (review-edit id review)))

   (context
    "/user/:id" []
     :auth-rules access/authenticated?
     :path-params [id :- Long]
     (GET "/follow" [] (user-follow id))
     (GET "/unfollow" {{referer "referer"} :headers} (user-unfollow id referer))))
