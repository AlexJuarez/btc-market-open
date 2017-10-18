(ns flight.util.core
  (:require
   [flight.db.core :refer [users currency]]
   [korma.core :refer [select with where fields]]
   [clojure.tools.logging :as log]
   [flight.cache :as cache]
   [flight.util.session :as session]
   [clojure.string :as s]
   [flight.models.currency :as currency]
   [flight.models.exchange :as exchange])
  (:import
    [org.apache.commons.codec.binary Base64]))

(defn page-max [items per-page]
  {:pre (> 0 per-page)}
  (let [items (or items 0)]
    (cond
      (> items per-page) (int (Math/ceil (/ items per-page)))
      :else 1)))

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

(defn create-uuid [string]
  "creates a uuid from a string"
  (try
    (java.util.UUID/fromString string)
    (catch Exception ex
      (log/error "Could not create uuid from " string)
      (throw ex))))

(defn user-id []
  (session/get :user_id))

(defmacro update-session
  [user-id & terms]
  `(let [id# ~user-id
         user-id# (session/get :user_id)]
     (if (= id# user-id#)
       (dorun (map session/remove! (list :user ~@terms)))
       (let [user# (first (select users (fields :session) (where {:id id#})))]
         (when (:session user#)
           (let [session# (.toString (:session user#))
                 sess# (cache/get session#)
                 ttl# (* 60 60 10)]
             (when sess#
               (cache/set session#
                          (assoc sess# :noir (dissoc (:noir sess#) ~@terms :user)) ttl#))))))))

(defn convert-price [price from to]
  (if-not (= from to)
    (let [rate (exchange/get from to)
          to-rate (exchange/get to from)]
      (cond
        (not (nil? rate))
        (* price rate)
        (not (nil? to-rate))
        (/ price to-rate)
        :else
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
        (convert-price price currency_id user_currency)
        (convert-price (convert-price currency_id 1 price) 1 user_currency)))))

(defn params [params]
  (s/join "&" (map #(str (name (key %)) "=" (val %)) params)))

(defn format-time
    "formats the time using SimpleDateFormat, the default format is
       \"dd MMM, yyyy\" and a custom one can be passed in as the second argument"
    ([time] (format-time time "dd MMM, yyyy"))
    ([time fmt]
         (.format (new java.text.SimpleDateFormat fmt) time)))

(defn bytes-to-base64 [bytes]
  (.toString (Base64/encodeBase64String bytes)))

(defn generate-salt []
  (let [b (byte-array 20)]
    (.nextBytes (java.security.SecureRandom.) b)
    (bytes-to-base64 b)))

(defn parse-int [s]
  (try (java.lang.Long/parseLong s)
       (catch Exception ex s)))
