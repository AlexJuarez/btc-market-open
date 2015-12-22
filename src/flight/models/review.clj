(ns flight.models.review
  (:refer-clojure :exclude [get])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core]))

(defn get [id user-id]
  (first (select reviews
          (with listings (fields :title))
          (where {:id id :user_id user-id}))))

(defn all [listing-id page per-page]
  (select reviews
          (where {:listing_id listing-id})
          (order :created_on :asc)
          (offset (* (- page 1) per-page))
          (limit per-page)))

(defn for-user [user-id page per-page]
  (select reviews
          (with listings
                (fields :title))
          (where {:user_id user-id})
          (offset (* (- page 1) per-page))
          (limit per-page)))

(defn for-seller [user-id]
  (select reviews
          (with listings
                (fields :title))
          (where {:seller_id user-id})
          (limit 20)))

(defn prep [{:keys [order_id rating content shipped]} user-id order-info]
  (if-let [order-info (order-info order_id)]
    {:order_id order_id
     :published true
     :seller_id (:seller_id order-info)
     :listing_id (:listing_id order-info)
     :rating (max 0 (min 5 rating))
     :content content
     :shipped (= "true" shipped)
     :user_id user-id}))

(defn update! [id {:keys [rating shipped content]} user_id]
  (let [rating (max 0 (min 5 rating))
        shipped (= "true" shipped)]
    (update reviews
            (set-fields {:rating rating :shipped shipped :content content})
            (where {:id id :user_id user_id}))))

(defn store! [{:keys [order_id seller_id listing_id rating user_id] :as review}]
      (transaction
        (update orders
                (set-fields {:reviewed true})
                (where {:id order_id}))
        (update listings
                (set-fields {:reviews (raw "reviews + 1") :rating (raw (str "(1.0*rating*reviews)/(reviews+1) + (" rating "*1.0)/(reviews+1)"))})
                (where {:id listing_id}))
        (update users
                (set-fields {:transactions (raw "transactions + 1") :rating (raw (str "(1.0*rating*transactions)/(transactions+1) + (" rating "*1.0)/(transactions+1)"))})
                (where {:id seller_id}))
        (update users
                (set-fields {:reviewed (raw "reviewed + 1")})
                (where {:id user_id}))
        (insert reviews
                (values review))))

(defn add! [data user-id order-ids]
  (let [os (select orders (where {:id [in order-ids] :user_id user-id :reviewed false}))
        order-info (apply merge (map #(hash-map (:id %) %) os))
        prepped (map #(prep % user-id order-info) data)]
  (dorun (pmap store! prepped))))
