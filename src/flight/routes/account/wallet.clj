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
    [flight.util.session :as session]
    [flight.util.pgp :as pgp]
    [schema.core :as s]))

(defn withdrawal [{:keys [amount address pin] :as slug}]
  (let [errors (:errors (user/withdraw-btc! slug (user-id)))
        user (util/current-user)
        transactions (audit/all (user-id))]
    (layout/render "account/wallet.html" {:amount amount :address address
                                          :errors errors :transactions transactions
                                          :balance (not (= (:currency_id user) 1))})))
(defn change-pin [slug]
  (let [errors (:errors (user/update-pin! slug (user-id)))
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

(s/defschema Wallet
  {(s/optional-key :pin) (s/both String (is-alphanumeric?) (in-range? 4 64))
   (s/optional-key :confirmpin) (s/both String (is-alphanumeric?) (in-range? 4 64))
   (s/optional-key :oldpin) String
   (s/optional-key :amount) (s/both Double (in-range? 0 (:btc (util/current-user))))
   (s/optional-key :address) String})

(defroutes wallet-routes
  (context
    "/wallet" []
    (GET "/" [] (wallet-page))
    (POST "/" []
          :form [wallet Wallet]
          (wallet-page wallet))
    (GET "/new" [] (wallet-new))))
