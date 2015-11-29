(ns flight.models.exchange
  (:refer-clojure :exclude [get])
  (:use
   [flight.db.core]
   [korma.core]
   [korma.db :only (transaction)]
   [clojure.string :only (split lower-case)])
  (:require
   [taoensso.timbre :as log]
   [cheshire.core :as jr]
   [flight.cache :as cache]
   [flight.models.currency :as currency]
   [clj-http.client :as client]))

(defonce remote-opts
  {:conn-timeout 1000
   :content-type :json
   :follow-redirects false
   :as :json
   :accept :json})

(defn update-from-remote []
  (let [response 
              (try 
                (:body (client/get "https://coinbase.com/api/v1/currencies/exchange_rates" remote-opts))
                (catch Exception ex
                  (log/error ex "getting the information from coinbase failed")
                  (jr/parse-string (slurp "resources/exchange_rates.json"))))
        currencies (apply merge (map #(assoc {} (lower-case (:key %)) (:id %)) (currency/all)))
        prep (filter #(not (or (nil? (:from %)) (nil? (:to %)))) (map #(let [s (split (name (key %)) #"_")] {:from (currencies (.substring (first s) 0 3)) :to (currencies (.substring (last s) 0 3)) :value (Float/parseFloat (val %))}) response))]
    (if-not (empty? response)
      (do
        (dorun (pmap #(cache/set (str (:from %) "-" (:to %)) (:value %)) prep))
        (transaction
          (delete exchange)
          (insert exchange
                  (values prep)))))))

(defn get [from to]
  (when-not (or (nil? from) (nil? to))
    (do
      (cache/cache! (str from "-" to)
          (:value (first (select exchange
                (where {:from from :to to}))))))))
