(ns flight.routes.vendor.news
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [schema.core :as s]
    [flight.models.post :as post]
    [ring.util.response :as resp]
    [flight.layout :as layout]
    [flight.util.markdown :as md]
    [flight.util.core :as util :refer [user-id current-user]]))

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

(defn news-edit
  ([id]
   (let [ article (post/get id)
         content (md/md->html (:content article))]
     (layout/render "news/create.html" article {:preview content})))
  ([id slug]
   (let [article (post/update! slug (user-id))
         content (md/md->html (:content article))]
     (layout/render "news/create.html" article {:preview content}))))

(s/defschema Article
  {(s/optional-key :subject) String
   (s/optional-key :public) Boolean
   (s/optional-key :published) Boolean
   (s/optional-key :content) String})

(defroutes vendor-routes
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
        (GET "/delete" [] (news-delete id)))))
