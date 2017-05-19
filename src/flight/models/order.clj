(ns flight.models.order
  (:refer-clojure :exclude [count update])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core])
  (:require
    [flight.env :refer [env]]
    [flight.models.postage :as postage]
    [flight.models.listing :as listings]
    [flight.validator :as v]
    [flight.util.session :as session]
    [flight.util.core :as util]))

;;Order status numbers
;;0 - processing
;;1 - shipping
;;2 - resolution
;;3 - finalized
;;4 - canceled
;;5 - refunded

(def statuses [:new :ship :resolution :finalize :canceled :refunded])

(defn get-order [id user-id]
  (first (select orders
                 (with sellers (fields :login :alias))
                 (where {:id id :user_id user-id}))))

(defn exists? [id user-id]
  (not (nil? (get-order id user-id))))

(defn get-sale [id seller-id]
  (first (select orders
                 (with users (fields :login :alias))
                 (where {:id id :seller_id seller-id}))))
(defn all [id]
  (select orders
          (with sellers (fields :login :alias))
          (where (and (= :user_id id)
                      (or (< :status 3) (not :reviewed))))
          (order :created_on :desc)))

(defn sold* [seller-id page per-page]
  (-> (select* orders)
      (with users (fields :login :alias))
      (with postage (fields [:title :postage_title]))
      (where {:seller_id seller-id})
      (order :created_on :desc)
      (offset (* (- page 1) per-page))
      (limit per-page)))

(defn sold
  ([id page per-page]
   (-> (sold* id page per-page)
       select))
  ([status id page per-page]
   (-> (sold* id page per-page)
       (where {:status status})
       select)))

(defn check-item [item]
  (let [id (key item)
        {:keys [quantity postage] :as raw} (val item)
        listing (listings/get id)
        check (assoc raw :max (:quantity listing) :user_id (:user_id listing))
        errors (v/cart-order-validator check)]
    (when-not (empty? errors)
      {id errors})))

(defn store! [order user-id pin]
  (let [currency_id (if (:hedged order) (:currency_id order) 1)
        item-cost (util/convert-price (:price order) (:currency_id order) currency_id)
        postage-cost (util/convert-price (:postage_price order) (:postage_currency order) currency_id)
        cost (+ (* (:quantity order) item-cost) postage-cost)
        btc_cost (util/convert-price cost currency_id 1) ;;use an env flag for this
        {lq :listing_quantity lp :listing_pubic cat_id :category_id} order
        {:keys [user_id seller_id listing_id quantity] :as order} (dissoc order :listing_quantity :listing_pubic :category_id)
        escr {:from user_id :hedged (:hedged order) :to seller_id :currency_id currency_id :amount cost :btc_amount btc_cost :status "hold"}
        audit {:user_id user-id :role "purchase" :amount (* -1 btc_cost)}]
    (util/update-session seller_id :sales)
    (let [order (transaction
                  (insert audits (values audit))
                  (update users (set-fields {:btc (raw (str "btc - " btc_cost))}) (where {:id user-id :pin pin}))
                  (update listings
                          (set-fields {:updated_on (raw "now()") :quantity (raw (str "quantity - " quantity))})
                          (where {:id listing_id}))
                  (if (and lp (<= (- lq quantity) 0)) (update category (set-fields {:count (raw "count - 1")}) (where {:id cat_id})))
                  (insert orders (values order)))]
      (if (not (empty? order));;TODO: what does korma return when it errors out?
        (transaction
          (insert order-audit (values {:status 0 :order_id (:id order) :user_id user-id}))
          (insert escrow (values (assoc escr :order_id (:id order)))))))))

(defn cancel!
  ([{:keys [id seller_id user_id listing_id quantity] :as order}]
   (let [escr (first (select escrow (where {:status "hold" :order_id id})))
         listing (first (select listings (where {:id listing_id})))]
     (when-not (empty? escrow)
       (util/update-session seller_id :sales :orders)
       (util/update-session user_id :sales :orders)
       (transaction
         (update escrow (set-fields {:status "refunded"}) (where {:status "hold" :order_id id}))
         (update listings
                 (set-fields {:updated_on (raw "now()") :quantity (raw (str "quantity + " quantity))})
                 (where {:id listing_id}))
         (insert audits (values {:user_id user_id :role "refund" :amount (:btc_amount escr)}))
         (update users (set-fields {:btc (raw (str "btc + " (:btc_amount escr)))}) (where {:id user_id}))
         (insert order-audit (values {:user_id user_id :status 4 :order_id id}))
         (if (and (:public listing) (<= (- (:quantity listing) quantity) 0))
           (update category (set-fields {:count (raw "count + 1")}) (where {:id (:category_id listing)})))
         (update orders (set-fields {:status 4 :reviewed true}) (where {:id id :finalized false}))))))
  ([id user-id]
   (let [order (first (select orders (where {:id id :status 0})))]
     (if (or (= (:seller_id order) user-id)
             (= (:user_id order) user-id))
       (cancel! order)))))

(defn prep [item address user-id]
  (let [id (key item)
        postid (:postage (val item))
        quantity (:quantity (val item))
        post (postage/get postid)
        listing (listings/get id)]
    {:price (:price listing)
     :postage_price (:price post)
     :postage_title (:title post)
     :postage_currency (:currency_id post)
     :hedge_fee (:hedge_fee listing)
     :quantity quantity
     :listing_quantity (:quantity listing) ;;this field gets removed
     :listing_pubic (:public listing) ;;placeholder
     :category_id (:category_id listing)
     :hedged (:hedged listing)
     :title (:title listing)
     :address address
     :seller_id (:user_id listing)
     :currency_id (:currency_id listing)
     :listing_id id
     :postage_id postid
     :user_id user-id
     :status 0}))

(defn add! [cart total address pin user-id]
  (let [cart-check (let [cart (reduce merge (map check-item cart))] (when-not (empty? cart) {:cart cart}))
        postage (some false? (map #(not (nil? (:postage (val %)))) cart-check))
        postage-error (when postage {:postage postage})
        check (v/cart-validator {:address address :pin pin :total total :user_id user-id})
        errors (merge cart-check postage-error check)]
    (if (empty? errors)
      (do
        (session/put! :cart {})
        (util/update-session user-id :orders :sales)
        (doall (map #(store! (prep % address user-id) user-id pin) cart)))
      {:address address :errors errors})))

(defn add-audit [user-id id status]
  (insert order-audit
          (values {:user_id user-id :order_id id})))

(defn update-sales [sales seller-id status]
  (when (not (empty? sales))
    (if (= status 1) (util/update-session seller-id :sales :orders))
    (let [statuses {:status status :updated_on (raw "(now())")}
          statuses (if (= status 1) (assoc statuses :auto_finalize (raw "(now() + interval '17 days')")) statuses)
          audits (map #(hash-map :user_id seller-id :order_id % :status status) sales)]
      (transaction
        (update orders
                (set-fields statuses)
                (where {:seller_id seller-id :finalized false :id [in sales]}))
        (update orders
                (set-fields {:status 3})
                (where {:seller_id seller-id :finalized true :id [in sales]}))
        (insert order-audit
                (values audits))))))

;;use update instead of select... genius
(defn finalize [id user-id]
  (util/update-session user-id :orders :sales)
  (let [{:keys [hedge_fee seller_id listing_id hedged]} (first (select orders (where {:id id :finalized false :user_id user-id})))
        {amount :amount currency_id :currency_id} (first (select escrow (where {:order_id id :from user-id})))
        amount (util/convert-price amount currency_id 1)
        percent (if hedged hedge_fee (env :fee))
        fee_amount (* percent amount)
        audit {:amount (- amount fee_amount) :user_id seller_id :role "sale"}
        fee {:order_id id :role "order" :amount fee_amount}]
    (when (not (nil? listing_id))
      (transaction
        (update orders (set-fields {:finalized true :updated_on (raw "now()")}) (where {:id id :finalized false :user_id user-id}))
        (update escrow (set-fields {:status "done" :updated_on (raw "now()")}) (where {:order_id id :from user-id :status "hold"}))
        (insert fees (values fee))
        (insert audits (values audit))
        (insert order-audit (values {:user_id user-id :status 3 :order_id id}))
        (update users (set-fields {:btc (raw (str "btc + " (- amount fee_amount)))}) (where {:id seller_id}))
        (update listings (set-fields {:sold (raw "sold + 1") :updated_on (raw "now()")}) (where {:id listing_id})))
      (util/update-session seller_id))))

(defn resolution [id user-id]
  (util/update-session user-id :orders :sales)
  (let [{:keys [seller_id] :as order}
        (first (select orders
                       (where {:status 1 :auto_finalize [< (raw "(now() + interval '5 days')")] :user_id user-id :id id})))]
    (transaction
      (update orders
              (set-fields {:status 2 :updated_on (raw "now()")})
              (where {:status 1 :auto_finalize [< (raw "(now() + interval '5 days')")]
                      :user_id user-id :id id}))
      (insert order-audit
              (values {:user_id user-id :order_id id :status 2}))
      (update users
              (set-fields {:resolutions (raw "resolutions + 1")})
              (where {:id [in [user-id seller_id]]})))

    order))

(defn moderate [page per-page]
  (select orders
          (with escrow (fields :btc_amount))
          (where {:status 2 :finalized false :auto_finalize [< (raw "now()")]})
          (offset (* (- page 1) per-page))
          (limit per-page)))

(defn moderate-order [id]
  (first (select orders
                 (with sellers (fields :login :alias))
                 (where {:id id :auto_finalize [< (raw "now()")]}))))

(defn past-resolutions [user-id]
  (select orders
          (fields :id :title :quantity :auto_finalize :listing_id :modresolution.percent)
          (with escrow (fields :escrow.btc_amount))
          (join modresolutions (= :modresolution.order_id :order.id))
          (where {:status 5 :user_id user-id :modresolution.applied true})
          (order :auto_finalize :DESC)
          (limit 10)))

(defn past-seller-resolutions [user-id]
  (select orders
          (fields :id :title :quantity :auto_finalize :listing_id :modresolution.percent)
          (with escrow (fields :escrow.btc_amount))
          (join modresolutions (= :modresolution.order_id :order.id))
          (where {:status 5 :seller_id user-id :modresolution.applied true})
          (order :auto_finalize :DESC)
          (limit 10)))

;;cancel button does not work
;;make sure to separate logic here
;;so that catagories and things are updated as the sales are rejected.
(defn reject-sales [sales seller-id]
  (when (not (empty? sales))
    (let [o (select orders
                    (where {:seller_id seller-id :finalized false :status 0 :id [in sales]}))]
      (dorun (map #(cancel! %) o)))))

(defn count [id]
  (:cnt (first (select orders
                       (aggregate (count :*) :cnt)
                       (where {:user_id id :status [in (list 0 1 2)]})))))

(defn count-past [id]
  (:cnt (first (select orders
                       (aggregate (count :*) :cnt)
                       (where {:user_id id :status 3})))))

;;map this into vector of status cnt's into a hash
(defn count-sales
  ([id]
   (let [sales
         (into {}
               (map #(vector (get statuses (:status %)) (:cnt %))
                    (select orders
                            (fields :status)
                            (aggregate (count :*) :cnt)
                            (where {:seller_id id})
                            (group :status))))]
     (assoc sales :total (reduce + (vals sales))))))
