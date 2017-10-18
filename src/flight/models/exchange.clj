(ns flight.models.exchange
  (:refer-clojure :exclude [get update])
  (:use
   [flight.db.core]
   [korma.core]
   [korma.db :only (transaction)]
   [clojure.string :only (split lower-case)])
  (:require
   [flight.env :refer [env]]
   [clojure.tools.logging :as log]
   [cheshire.core :as jr]
   [flight.cache :as cache]
   [clj-http.client :as client]))

(defonce remote-opts
  {:conn-timeout 1000
   :content-type :json
   :follow-redirects false
   :as :json
   :accept :json})

(defn- get-rate [from to]
  (when-let [val (first (select exchange (where {:from from :to to})))]
    (:value val)))

(defn- get-from-remote [url]
  (try
    (:body (client/get url remote-opts))
    (catch Exception ex
      (do
        (log/error "Fetch from coinbase failed")
        (throw ex))
      (jr/parse-string (slurp "resources/exchange_rates.json")))))

(defn- get-currency-map [currencies]
  (->>
   (map #(assoc {} (lower-case (:key %)) (:id %)) currencies)
   (apply merge)))

(defn- create-currency-map [slug]
  (let [currencies (get-currency-map (select currency))]
    (->>
     (map #(let [s (split (name (key %)) #"_")]
             {:from (currencies (.substring (first s) 0 3))
              :to (currencies (.substring (last s) 0 3))
              :value (Float/parseFloat (val %))})
          slug)
     (filter #(not (or (nil? (:from %)) (nil? (:to %))))))))

(defn update-from-remote []
  (let [response (get-from-remote (env :remote-bitcoin-values))
        prep (create-currency-map response)]
    (if (not (empty? response))
      (transaction
       (delete exchange)
       (insert exchange
               (values prep))))))

(defn get [from to]
  (when-not (or (nil? from) (nil? to))
    (cache/cache! (str from "-" to)
      (get-rate from to))))
