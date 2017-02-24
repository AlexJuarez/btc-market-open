(ns flight.util.cart
  (:require
    [flight.util.core :as util]
    [flight.models.listing :as listing]
    [flight.util.session :as session]
    [flight.util.error :as error]
    [flight.models.postage :as postage]
    [flight.models.listing :as listing]
    [flight.models.currency :as currency]
    [flight.models.order :as orders]
    [schema.core :as s]
    [flight.routes.helpers :refer [in-range?]]
    [flight.util.exception :refer [stringify-error]]))

(def cart-limit 100)

(defn item-schema [id]
  {:id (s/both Long (s/pred listing/exists? 'exists?))
   :postage (s/both Long (s/pred postage/exists? 'exists?))
   :quantity (s/both Long (in-range? 0 (:quantity (listing/get id))))})

(defn cart []
  (or (session/get :cart) {}))

(defn size []
  (count (cart)))

(defn add!
  ([id]
   (when (< (size) cart-limit)
     (session/update-in! [:cart id :quantity] (fnil inc 0)))
     (session/assoc-in! [:cart id :id] id))
  ([id postage]
   (add! id)
   (when (not (nil? postage))
     (session/assoc-in! [:cart id :postage] postage))))

(defn remove! [id]
  (session/update-in! [:cart] dissoc id))

(defn empty! []
  (session/put! :cart {}))

(defn postage-price [id postages]
  (let [postages (into {} (map #(vector (:id %1) %1) postages))
        {:keys [price] :as postage} (postages id)]
    (if (nil? postage)
      0
      price)))

(defn calculate-listing-price [{:keys [price currency_id postage lid] :as listing}]
  (let [listing-total (* price (get-in (cart) [lid :quantity]))
        postage-total (postage-price (get-in (cart) [lid :postage]) postage)
        errors (or (get-in (error/all) [:cart (-> lid str keyword)]) {})]
  (assoc listing :total (+ listing-total postage-total) :subtotal listing-total :errors errors)))

(defn listings
  ([]
   (let [ids (map #(:id %) (vals (cart)))]
     (map #(calculate-listing-price %1) (listing/get-in ids)))))

(defn total
  ([]
   (or (reduce + (map #(:total %) (listings))) 0))
  ([currency_id]
   (util/convert-price (:currency_id (util/current-user)) currency_id (total))))

(defn- filter-nil [map]
    (into {} (remove (comp nil? second) map)))

(defn check []
  (->
    (reduce-kv (fn [m k v] (assoc m k (s/check (item-schema k) v))) {} (cart))
    filter-nil))

(defn prep-item [{:keys [id] :as item}]
  (let [errors (s/check (item-schema id) item)]
    (if (empty? errors)
      item
      (do
        (error/assoc-in! [:cart (-> id str keyword)] (stringify-error errors))
        (apply dissoc item (keys errors))))))

(defn update!
  ([items]
   (doall (map #(update! (:id %1) %1) (vals items))))
  ([id item]
   (session/update-in! [:cart id] #(merge %1 (prep-item item)))))
