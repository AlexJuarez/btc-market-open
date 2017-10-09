(ns flight.routes.message
  (:require
    [compojure.api.sweet :refer :all]
    [flight.routes.helpers :refer :all]
    [flight.layout :as layout]
    [flight.models.user :as user]
    [ring.util.response :as r
     :refer                 [content-type response]]
    [flight.models.message :as message]
    [flight.models.post :as post]
    [flight.models.currency :as currency]
    [flight.util.core :as util
     :refer               [user-id]]
    [flight.util.error :as error]
    [flight.util.hashids :as hashids]
    [flight.env :refer [env]]
    [schema.core :as s]
    [ring.util.response :as resp]
    [clojure.string :as string]
    [mount.core :refer [defstate]]
    [flight.access :as access]))

(defonce per-page 25)

(defn encrypt-feedback-ids [messages]
  (map
    #(assoc % :feedback_id (when (:feedback_id %) (hashids/encrypt-ticket-id (:feedback_id %))))
    messages))

(defn messages-page [page]
  (let [pagemax  (util/page-max (:total (util/session! :messages (message/count (user-id)))) per-page)
        news     (post/get-news (user-id))
        messages (encrypt-feedback-ids (message/all (user-id) page per-page))]
    (layout/render "messages/index.html"
                   {:paginate {:page page :max pagemax}
                    :news     news
                    :messages messages})))

(defpage support-page
  :template ["messages/thread.html"
             {:receiver {:alias "Support Staff"}
              :no_subject true
              :messages (fn [ticket-id] (message/all (hashids/decrypt-ticket-id ticket-id)))}]
  :args [:ticket-id]
  (fn [slug ticket-id]
    (let [tid (hashids/decrypt-ticket-id ticket-id)]
      (message/add-support! slug tid (user-id)))))

(defn messages-sent []
  (layout/render "messages/sent.html" {:messages (message/sent (user-id))}))

(defn message-delete [message-id referer]
  (message/remove! message-id (user-id))
  (resp/redirect referer))

(defn messages-download [receiver-id]
  (let [user_id     (user-id)
        my-name     (:alias (util/current-user))
        messages    (message/all (user-id) receiver-id)
        sender_name (:user_alias (first messages))
        messages    (string/join "\n"
                                 (map
                                   #(str "\"" (if (= (:sender_id %) user_id) my-name (:user_alias %)) "\",\""
                                     (:created_on %)
                                     "\",\""
                                     (string/replace (:content %) #"[\"]" "\"\"")
                                     "\"")
                                   messages))]
    (-> (response messages)
        (content-type "text/plain")
        (r/header "Content-Disposition"
                  (str "attachment;filename=" (util/format-time (java.util.Date.) "MM-dd-yyyy") "-" sender_name "-conversation.csv")))))

(defpage thread-page
  :template ["messages/thread.html"
   {:receiver (fn [id] (user/get id))
    :messages (fn [id] (message/all (user-id) id))}]
  :args [:id]
  (fn [slug id]
    (message/add! slug (user-id) id)))

(s/defschema Message
  {:subject (Str 0 100)
   :content (Str 6000)})

(defroutes user-routes
  (context
    "/messages" []
    :tags ["user"]
    :access-rule access/user-authenticated
    (GET "/" []
         :query-params [{page :- Long 1}]
         (messages-page page))
    (GET "/sent" [] (messages-sent))
    (context "/:id" []
             :path-params [id :- (s/both Long (s/pred user/exists? 'exists?))]
             (GET "/" [] (thread-page id))
             (GET "/download" [] (messages-download id))
             (POST "/" []
                   :form [message Message]
                   (thread-page message id))))
  (context
    "/message/:id" []
    :tags        ["user"]
    :access-rule access/user-authenticated
    :path-params [id :- (s/both Long (s/pred message/exists? 'exists?))]
    (GET "/delete" {{referer "referer"} :headers} (message-delete id referer)))
  (context
    "/support/:tid" [tid]
    :path-params [tid :- Long]
    :tags        ["user"]
    :access-rule access/user-authenticated
    (GET "/" [] (support-page tid))
    (POST "/" []
          :form [message Message]
          (support-page tid message))))
