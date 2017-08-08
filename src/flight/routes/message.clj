(ns flight.routes.message
  (:require
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout]
    [flight.models.user :as user]
    [ring.util.response :as r
     :refer                 [content-type response]]
    [flight.models.message :as message]
    [flight.models.post :as post]
    [flight.routes.helpers :refer :all]
    [flight.models.currency :as currency]
    [flight.util.core :as util
     :refer               [user-id]]
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
    #(assoc % :feedback_id (if (not (nil? (:feedback_id %))) (hashids/encrypt-ticket-id (:feedback_id %))))
    messages))

(defn messages-page [page]
  (let [pagemax  (util/page-max (:total (util/session! :messages (message/count (user-id)))) per-page)
        news     (post/get-news (user-id)) ;;TODO: add pagination
        messages (encrypt-feedback-ids (message/all (user-id) page per-page))]
    (layout/render "messages/index.html"
                   {:paginate {:page page :max pagemax}
                    :news     news
                    :messages messages})))

(defn support-thread
  ([ticket-id]
    (let [tid (hashids/decrypt-ticket-id ticket-id)]
      (layout/render "messages/thread.html"
                     {:has_pub_key false
                      :alias       "Support Staff"
                      :no_subject  true
                      :messages    (message/all tid)})))
  ([ticket-id slug]
    (let [tid     (hashids/decrypt-ticket-id ticket-id)
          message (message/add-support! slug tid (user-id))]
      (layout/render "messages/thread.html"
                     {:has_pub_key false
                      :alias       "Support Staff"
                      :no_subject  true
                      :messages    (message/all tid)}
                     message))))

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

(defn messages-thread
  ([receiver-id]
    (let [receiver (user/get receiver-id)]
      (layout/render "messages/thread.html"
                     {:has_pub_key (not (nil? (:pub_key receiver)))
                      :user_id     receiver-id
                      :alias       (:alias receiver)
                      :messages    (message/all (user-id) receiver-id)}))))

(defn message-create [message receiver-id]
  (let [message  (message/add! message (user-id) receiver-id)
        receiver (user/get receiver-id)]
    (layout/render "messages/thread.html"
                   {:has_pub_key (not (nil? (:pub_key receiver)))
                    :alias       (:alias receiver)
                    :user_id     (:id receiver)
                    :messages    (message/all (user-id) (:id receiver))}
                   message)))

(s/defschema Message
  {(s/optional-key :subject) (Str 100)
   :content                  (Str 6000)})

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
             (GET "/" [] (messages-thread id))
             (GET "/download" [] (messages-download id))
             (POST "/" []
                   :form [message Message]
                   (message-create message id))))
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
    (GET "/" [] (support-thread tid))
    (POST "/" []
          :form [message Message]
          (support-thread tid message))))
