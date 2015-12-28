(ns flight.layout.helpers
  (:require
   [flight.util.core :as util]
   [flight.models.order :as order]
   [flight.models.message :as message]
   [flight.util.session :as session]))

(defn- is-user-logged-in? []
  (and
   (not (nil? (session/get :user_id)))
   (session/get :authed)))

(defn get-info []
  (merge
   {:cart-count (count (session/get :cart))}
   (if (is-user-logged-in?)
     (let [{:keys [id vendor] :as user} (util/current-user)]
       {:user
        (merge user
               {:logged_in true
                :conversion (util/convert-currency 1 1)}
               {:balance (util/convert-currency 1 (:btc user))}
               (when vendor {:sales (util/session! :sales (order/count-sales id))})
               {:orders (util/session! :orders (order/count id))
                :messages (util/session! :messages (message/count id))})})
     {:user {:currency_symbol "$"
             :conversion (util/convert-price 1 26 1)
             :currency_id 26}})))
