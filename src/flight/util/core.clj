(ns flight.util.core
  (:require
   [clojure.string :as s]
   [flight.util.user :as user-util]
   [flight.models.exchange :as exchange]))

(defn convert-price [from to price]
  (if-not (= from to)
    (let [rate (exchange/get from to)]
         (if-not (nil? rate)
           (* price rate)
           price))
    price))

(defn convert-currency
  "converts a currency_id to the users preferred currency
   takes a currency_id and price"
  ([{:keys [currency_id price]}]
   (convert-currency currency_id price))
  ([currency_id price]
    (let [user_currency (:currency_id (user-util/current))
          currencies [1 26]]
      (if (or
           (= user_currency currency_id)
           (some #(= user_currency %) currencies)
           (some #(= currency_id %) currencies))
        (convert-price currency_id user_currency price)
        (convert-price 1 user_currency (convert-price currency_id 1 price))))))

(defn params [params]
  (s/join "&" (map #(str (name (key %)) "=" (val %)) params)))
