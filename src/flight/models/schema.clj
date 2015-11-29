(ns flight.models.schema
  (:use [lobos.core :only (defcommand migrate rollback)])
  (:require [noir.io :as io]
            [cheshire.core :as jr]
            [taoensso.timbre :as timbre]
            [flight.models.currency :as c]
            [flight.models.category :as cat]
            [flight.models.exchange :as e]
            [flight.models.region :as region]
            [flight.db :as db]
            [lobos.migration :as lm]))

(defcommand pending-migrations []
  (lm/pending-migrations db/db-spec sname))

(defn load-regions []
  (region/add! (jr/parse-string (slurp "resources/regions.json"))))

(defn load-currencies []
  (c/add! (distinct
           (jr/parse-string (slurp "resources/currencies_symbols.json") true))))


(defn load-fixtures []
  (when (empty? (cat/all false))
    (load-regions)
    (load-currencies)
    (e/update-from-remote)
    (cat/load-fixture)))

(defn actualized?
    "checks if there are no pending migrations"
    []
    (empty? (pending-migrations)))

(def actualize migrate)
