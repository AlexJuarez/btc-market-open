(ns flight.routes.auth
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [ring.util.response :as resp]
   [flight.models.message :as message]
   [flight.models.order :as order]
   [flight.models.user :as users]
   [flight.routes.helpers :refer :all]
   [flight.util.captcha :as captcha]
   [flight.util.core :as util]
   [flight.util.pgp :as pgp]
   [flight.util.session :as session]
   [flight.util.error :as error]
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

(defn registration-page-validator
  [{:keys [pass confirm]}]
  (when-not
    (= pass confirm)
    (error/register! :pass "passwords do not match")))

(defpage registration-page
  :template ["register.html" {:captcha_img (fn [& _] (captcha/gen))}]
  :args [:cookies]
  :validator registration-page-validator
  (fn [{:keys [login pass confirm] :as slug} cookies]
    (users/add! {:login login :pass pass :confirm confirm})
    (let [session (-> (cookies "session") :value)
          user (users/login! login pass session)]
      (log/debug user)
      (finish-login user)
      (redirect-url))))

(defpage login-page
  :template ["login.html"]
  :args [:cookies]
  (fn [{:keys [login pass] :as slug} cookies]
    (let [session (-> (cookies "session") :value)
          user (users/login! login pass session)]
      (finish-login user)
      (if (session/get :authed)
        (redirect-url)
        (resp/redirect "/login/auth")))))

(defn gen-hashkey []
  (->>
    (range 6)
    (map (fn [_] (get words (rand-int 31))))
    (reduce str)))

(defpage auth-page
  :template
  ["auth.html"
   {:decode (fn []
              (let [pub_key (-> (util/current-user) :pub_key)
                    hashkey (gen-hashkey)
                    decode (pgp/encode pub_key hashkey)]
                (session/flash-put! :key hashkey)
                decode))}]
  (fn [{:keys [response]}]
    (session/put! :authed true)
    (redirect-url)))

(defn check-auth [text]
  (= (session/flash-get :key) text))

(s/defschema Login
  {:login (Str 3 64 (s/pred users/exists? '(exists? "User")))
   :pass (Str 0 73)})

(s/defschema Register
  {:login (Str 3 64 (is-alphanumeric?) (s/pred #(not (users/exists? %)) '(taken? "login")))
   :pass (Str 3 73)
   :confirm (Str 3 73)
   :captcha (Str 0 8 (s/pred valid-captcha? 'valid-captcha?))})

(s/defschema Response
  {:response (Str (s/pred check-auth 'check-auth))})

(defroutes public-routes
  (context
    "/login" []
    (GET "/"
         {{referer "referer"} :headers}
         (login-page referer))
    (POST "/" {cookies :cookies}
          :form [info Login]
          (login-page info cookies))
    (page-route "/auth" auth-page Response))
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
