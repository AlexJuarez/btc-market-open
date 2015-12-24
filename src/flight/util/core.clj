(ns flight.util.core
  (:use
   [flight.db.core]
   [korma.core])
  (:require
   [flight.cache :as cache]
   [flight.util.session :as session]
   [clojure.string :as s]
   [flight.models.exchange :as exchange]))

(defmacro session! [key func]
  `(let [value# (session/get ~key)]
    (if (nil? value#)
      (let [value# ~func]
        (session/put! ~key value#)
        value#)
      value#)))

(defn current-user []
  (session! :user
            (if (nil? (session/get :user_id))
              {:currency_id 26}
              (-> (select users (with currency (fields [:key :currency_key] [:symbol :currency_symbol]))
                          (where {:id (session/get :user_id)})) first (dissoc :salt :pass)))))

(defmacro update-session
  [user-id & terms]
    `(let [id# ~user-id
           user-id# (session/get :user_id)]
      (if (= id# user-id#)
        (doall (map session/remove! (list :user ~@terms)))
        (let [user# (first (select users (fields :session) (where {:id id#})))]
          (when (:session user#)
            (let [session# (.toString (:session user#))
                  sess# (cache/get session#)
                  ttl# (* 60 60 10)]
              (if (not (nil? sess#))
                (cache/set session#
                           (assoc sess# :noir (dissoc (:noir sess#) ~@terms :user)) ttl#))))))))

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
    (let [user_currency (:currency_id (current-user))
          currencies [1 26]]
      (if (or
           (= user_currency currency_id)
           (some #(= user_currency %) currencies)
           (some #(= currency_id %) currencies))
        (convert-price currency_id user_currency price)
        (convert-price 1 user_currency (convert-price currency_id 1 price))))))

(defn params [params]
  (s/join "&" (map #(str (name (key %)) "=" (val %)) params)))
