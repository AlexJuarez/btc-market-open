(ns flight.routes.auth
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [ring.util.response :as resp]
   [flight.layout :as layout]
   [flight.models.message :as message]
   [flight.models.order :as order]
   [flight.models.user :as users]
   [flight.routes.helpers :refer :all]
   [flight.util.captcha :as captcha]
   [flight.util.core :as util]
   [flight.util.error :as error]
   [flight.util.pgp :as pgp]
   [flight.util.session :as session]
   [slingshot.slingshot :refer [try+ throw+]]
   [schema.core :as s]
   [taoensso.timbre :as log]))

(defonce words ["the" "and" "for" "are" "but" "not" "can" "one" "day"
                "get" "man" "new" "now" "old" "two" "boy" "put" "her" "dad" "zoo"
                "tan" "saw" "mad" "jet" "far" "cat" "map" "key" "dog" "god" "bat"])

(defn redirect-check [url]
  (and (not (empty? url))
       (every? #(re-matcher (re-pattern %) url) ["register" "login"])))

(defn redirect-url [& [d]]
  (let [url (and (redirect-check (session/flash-get :redirect))
                 (session/flash-get :redirect))
        default (or d "/")
        url (or url default)]
    (resp/redirect url)))

(defn finish-login [{:keys [id vendor auth pub_key] :as user}]
  (when vendor (session/put! :sales (order/count-sales id)))
  (session/put! :authed (not (and auth (not (nil? pub_key)))))
  (session/put! :user_id id)
  (session/put! :user user)
  (session/put! :orders (order/count id))
  (session/put! :messages (message/count id)))

(defn valid-captcha? [text]
  (and
   (not (nil? (session/flash-get :captcha)))
   (= (:text
      (session/flash-get :captcha))
     text)))

(defn registration-page
  ([]
   (layout/render "register.html" {:captcha (captcha/gen)}))
  ([{:keys [login pass confirm] :as slug} cookies]
   (do
     (users/add! {:login login :pass pass :confirm confirm})
     (let [session (:value (cookies "session"))
           user (users/login! login pass session)]
       (if (error/empty?)
         (do
           (log/debug user)
           (finish-login user)
           (redirect-url))
         (layout/render "register.html" slug {:captcha (captcha/gen)}))))))

(defn login-page
  ([referer]
   (when referer (session/flash-put! :referer referer))
   (layout/render "login.html"))
  ([{:keys [login pass] :as slug} cookies]
   (let [session (:value (cookies "session"))
         user (users/login! login pass session)]
     (if (error/empty?)
       (do
         (finish-login user)
         (if (session/get :authed)
           (redirect-url)
           (resp/redirect "/login/auth")))
       (layout/render "login.html" slug)))))

(defn auth-page
  ([]
   (let [hashkey (reduce str (map #(if (or true %) (str (get words (rand-int 32)))) (range 6)))
         user (util/current-user)]
     (when user
       (session/flash-put! :key hashkey)
       (layout/render "auth.html" {:decode (pgp/encode (:pub_key user) hashkey)}))))
  ([{:keys [response]}]
   (if (error/empty?)
     (do
       (session/put! :authed true)
       (redirect-url))
     (auth-page))))

(defn check-auth [text]
  (= (session/flash-get :key) text))

(s/defschema Login
  {:login (s/both String (s/pred users/exists? 'exists?))
   :pass String})

(s/defschema Register
  {:login (s/both String (s/pred #(not (users/exists? %)) 'exists?))
   :pass String
   :confirm String
   :captcha (s/both String (s/pred valid-captcha? 'valid-captcha?))})

(defroutes public-routes
  (context
   "/login" []
   (GET "/"
         {{referer "referer"} :headers}
         (login-page referer))
   (POST "/" {cookies :cookies}
          :form [info Login]
          (login-page info cookies))
   (GET "/auth" []
         (auth-page))
   (POST "/auth" []
          :form [slug {:response (s/both String (s/pred 'check-auth 'check-auth))}]
          (auth-page slug)))
  (context
   "/register" []
   (GET "/" []
         (registration-page))
   (POST "/" {cookies :cookies}
          :form [info Register]
          (registration-page info cookies)
  ))
  (GET "/logout" []
        (session/clear!)
        (resp/redirect "/")))
