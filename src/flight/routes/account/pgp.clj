(ns flight.routes.account.pgp
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout :refer [error-page]]
    [flight.models.user :as user]
    [flight.util.core :as util :refer [user-id]]
    [flight.util.message :as message]
    [flight.util.error :as error]
    [ring.util.response :as resp]
    [flight.util.session :as session]
    [flight.util.pgp :as pgp]
    [schema.core :as s]))

(s/defschema PGP
  {:pub_key (Str 0 4000 (s/pred pgp/valid? 'pgp/valid?))})

(defpage pgp-page
  :template ["account/pgp.html"]
  (fn [slug]
    (session/put! :pub_key (:pub_key slug))
    (resp/redirect "/account/pgp/verify")))

(defn pgp-verify
  ([]
   (let [pub_key (session/get :pub_key)
         secret (util/generate-salt)
         decode (pgp/encode pub_key secret)]
     (session/flash-put! :pgp-verify secret)
     (layout/render "account/pgp-verification.html" {:decode decode :pub_key pub_key})))
  ([response]
   (let [secret (session/flash-get :pgp-verify)]
     (if (= secret response)
       (do
         (message/success! "success")
         (user/update-pgp! (session/get :pub_key) (user-id))
         (pgp-page))
       (do
         (message/warn! "please try again")
         (pgp-verify))))))

(defroutes pgp-routes
  (context
    "/pgp" []
    (page-route "/" pgp-page PGP)
    (GET "/verify" [] (pgp-verify))
    (POST "/verify" []
          :form-params [response :- String] (pgp-verify response))))
