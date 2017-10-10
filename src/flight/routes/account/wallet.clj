(ns flight.routes.account.wallet
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout :refer [error-page]]
    [flight.models.user :as user]
    [flight.models.audit :as audit]
    [flight.util.core :as util :refer [user-id]]
    [flight.util.error :as error]
    [ring.util.response :as resp]
    [flight.util.message :as message]
    [flight.util.session :as session]
    [flight.util.pgp :as pgp]
    [schema.core :as s]))

(defn pin-update-valiator [{:keys [pin confirmpin]}]
  (when-not (= pin confirmpin)
    (error/register! :pin "your pins do not match")))

(defn wallet-page
  ([& [slug]]
   (layout/render
     "account/wallet.html"
     {:transactions (audit/all (user-id))
      :balance (not (= (:currency_id (util/current-user) 1)))}
     slug)))

(defn- wallet-params []
  {:transactions (audit/all (user-id))
   :balance (not (= (:currency_id (util/current-user) 1)))})

(defpage wallet-pin-page
  :template ["account/wallet.html" (fn [] (wallet-params))]
  :success "Your pin has been changed."
  :validator pin-update-valiator
  (fn [slug]
    (user/update-pin! slug (user-id))))

(defn wallet-withdraw [{:keys [amount address pin] :as slug}]
  (when (error/empty?)
    (message/success! "Withdrawal successful")
    (user/withdraw-btc! slug (user-id))
    (resp/redirect "/account/wallet"))
  (wallet-page {:amount amount :address address}))

(defn wallet-new []
  (user/update-btc-address! (user-id))
  (resp/redirect "/account/wallet"))

(defn- confirm-pin [pin]
  (= pin (:pin (util/current-user))))

(s/defschema UpdatePin
  {:pin (Str 4 64 (is-alphanumeric?) (s/pred confirm-pin 'confirm-pin))
   :confirmpin (Str 4 64 (is-alphanumeric?))
   :oldpin (Str 4 64)})

(s/defschema Withdraw
  {:pin (Str 4 64 (is-alphanumeric?) (s/pred confirm-pin 'confirm-pin))
   :amount (s/both Double (in-range? 0 `(:btc (util/current-user))))
   :address (Str 0 34)})

(s/defschema NewPin
  {:pin (Str 4 64 (is-alphanumeric?))
   :confirmpin (Str 4 64 (is-alphanumeric?))})

(defroutes wallet-routes
  (context
    "/wallet" []
    (GET "/" [] (wallet-page))
    (POST "/changepin" []
          :form [slug UpdatePin]
          (wallet-pin-page slug))
    (POST "/withdraw" []
          :form [slug Withdraw]
          (wallet-withdraw slug))
    (POST "/newpin" []
          :form [slug NewPin]
          (wallet-pin-page slug))
    (GET "/new" [] (wallet-new))))
