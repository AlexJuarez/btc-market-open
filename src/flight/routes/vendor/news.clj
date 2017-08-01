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

(defpage news-create-page
  :template ["news/create.html"]
  (fn [slug] (resp/redirect (str "/vendor/news/" (:id slug) "/edit"))))

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
   (let [article (post/update! (assoc slug :id id) (user-id))
         content (md/md->html (:content article))]
     (layout/render "news/create.html" article {:preview content}))))

(s/defschema Article
  {(s/optional-key :subject) (Str 0 100)
   (s/optional-key :public) Boolean
   (s/optional-key :published) Boolean
   (s/optional-key :content) String})

(defroutes vendor-routes
  (context
      "/news" []
      (GET "/" [] (news-page))
      (page-route "/create" news-create-page Article)
      (context
        "/:id" []
        :path-params [id :- Long]
        (GET "/edit" [] (news-edit id))
        (POST "/edit" []
               :form [article Article]
               (news-edit id article))
        (GET "/publish" [] (news-publish id))
        (GET "/delete" [] (news-delete id)))))
