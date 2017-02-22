(ns flight.routes.account.pgp
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout :refer [error-page]]
    [flight.models.user :as user]
    [flight.util.core :as util :refer [user-id]]
    [flight.util.error :as error]
    [ring.util.response :as resp]
    [flight.util.session :as session]
    [flight.util.pgp :as pgp]
    [schema.core :as s]))

(s/defschema PGP
  {:pub_key (s/both String (less-than? 4000) (s/pred pgp/valid? 'pgp/valid?))})

(defn pgp-page
  ([]
   (layout/render "account/pgp.html" {:message (session/flash-get :message)}))
  ([{:keys [pub_key]}]
   (let [message (session/flash-get :pgp-message)]
     (if (error/empty?)
       (do
         (session/put! :pub_key pub_key)
         (resp/redirect "/account/pgp/verify"))
       (layout/render "account/pgp.html" {:pub_key pub_key})))))

(defn pgp-verify
  ([]
   (let [pub_key (session/get :pub_key)
         secret (util/generate-salt)
         decode (pgp/encode pub_key secret)
         message (session/flash-get :message)]
     (session/flash-put! :pgp-verify secret)
     (layout/render "account/pgp-verification.html" {:message message :decode decode :pub_key pub_key})))
  ([response]
   (let [secret (session/flash-get :pgp-verify)]
     (if (= secret response)
       (do
         (session/flash-put! :message "success")
         (user/update-pgp! (session/get :pub_key) (user-id))
         (pgp-page))
       (do
         (session/flash-put! :message "please try again")
         (pgp-verify))))))

(defroutes pgp-routes
  (context
    "/pgp" []
    (GET "/" [] (pgp-page))
    (POST "/" []
          :form [info PGP]
          (pgp-page info))
    (GET "/verify" [] (pgp-verify))
    (POST "/verify" []
          :form [response :- String] (pgp-verify response))))
