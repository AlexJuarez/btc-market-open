(ns flight.util.cart
  (:require
    [flight.util.core :as util]
    [flight.models.listing :as listing]
    [flight.util.session :as session]
    [flight.util.error :as error]))

(def cart-limit 100)

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

(defn calculate-listing-price [{:keys [price currency_id postage lid] :as listing} slug]
  (let [listing-total (* price (get-in (cart) [lid :quantity]))
        postage-total (postage-price (get-in (cart) [lid :postage]) postage)
        errors (or (get-in (error/all) [:cart (keyword (str lid))]) {})]
    (prn price listing-total (get-in (cart) [lid :quantity]))
  (assoc listing :total (+ listing-total postage-total) :subtotal listing-total :errors errors)))

(defn listings
  ([&[slug]]
   (let [ids (map #(:id %) (vals (cart)))]
     (map #(calculate-listing-price %1 slug) (listing/get-in ids)))))

(defn total
  ([]
   (or (reduce + (map #(:total %) (listings))) 0))
  ([currency_id]
   (util/convert-price (:currency_id (util/current-user)) currency_id (total))))

(defn update!
  ([items]
   (doall (map #(update! (:id %1) %1) (vals items))))
  ([id item]
   (session/update-in! [:cart id] #(merge %1 item))))
