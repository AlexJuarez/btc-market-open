(ns flight.layout.filters
  (:require
    [flight.layout.filters.paginate]
    [flight.layout.filters.freshness]
    [flight.layout.filters.core]
    [flight.layout.filters.category]
    [clojure.string :as s]
    [flight.util.session :as session]
    [flight.cache :as cache]
    [flight.models.region :as regions]
    [flight.models.currency :as currency]
    [flight.util.core :as util]
    [selmer.filters :refer :all]
    [hiccup.core :refer :all]))

(add-filter!
  :empty?
  (fn [x]
    (if (string? x) (s/blank? x) (empty? x))))

(add-filter!
  :rating (fn [x]
            (int (* (/ x 5.0) 100))))

(add-filter!
  :count-cart
  (fn [x]
    (:quantity ((session/get :cart) x))))

(add-filter!
  :postage-cart
  (fn [x]
    (:postage ((session/get :cart) x))))

(add-filter! :status
             (fn [x]
               (let [status ["processing" "shipping" "in resolution" "finalized" "canceled" "refunded"]]
                 (status x))))

(defn region [region_id]
  (let [regions (cache/cache! "regions_map" (into {} (map #(vector (:id %) (:name %)) (regions/all))))]
                 (regions region_id)))

(add-filter! :region region)

(add-filter! :regions (fn [regions]
                        (if (= regions [1])
                          "Worldwide"
                          (s/join ", " (map #(region %) regions)))))
