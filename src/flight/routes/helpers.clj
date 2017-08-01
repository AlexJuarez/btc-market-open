(ns flight.routes.helpers
  (:require
    [flight.util.session :as session]
    [flight.util.hashids :as hashids]
    [flight.util.core :as util :refer [user-id]]
    [clojure.string :as string]
    [taoensso.timbre :as log]
    [flight.env :refer [env]]))

(load "helpers/image")
(load "helpers/page")
(load "helpers/predicates")
(load "helpers/report")

(defn is-user-logged-in? []
  (and
   (not (nil? (session/get :user_id)))
   (session/get :authed)))

(defn convert-order-price [{:keys [price postage_price postage_currency currency_id quantity] :as order}]
  (when order
    (let [price (util/convert-currency order)
          postage (util/convert-currency postage_currency postage_price)
          total (+ (* price quantity) postage)]
    (-> order (assoc :price price
                     :total total
                     :postage_price postage)))))

(defn encrypt-id [m]
  (when m
    (assoc m :id (hashids/encrypt (:id m)))))

(defn encrypt-ids [l]
  (map encrypt-id l))

(defn estimate-refund [resolutions {:keys [total]}]
  (map #(if (= (:action %) "refund")
            (assoc % :est (* (/ (:value %) 100) total))
          %
         ) resolutions))

