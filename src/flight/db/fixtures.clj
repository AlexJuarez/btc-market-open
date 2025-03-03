(ns flight.db.fixtures
  (:require
   [cheshire.core :as jr]
   [clojure.tools.logging :as log]
   [flight.models.currency :as c]
   [flight.models.category :as cat]
   [flight.models.exchange :as e]
   [flight.models.region :as region]))

(defn load-regions []
  (region/add! (jr/parse-string (slurp "resources/regions.json"))))

(defn load-currencies []
  (c/add! (distinct
           (jr/parse-string (slurp "resources/currencies_symbols.json") true))))

(defn load-fixtures []
  (when (empty? (c/all false))
    (load-regions)
    (load-currencies)
    (e/update-from-remote)
    (cat/load-fixture)))
