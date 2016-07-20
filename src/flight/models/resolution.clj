(ns flight.models.resolution
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core])
  (:require
   [flight.util.core :as util]
   [flight.validator :as v]))

(def actions #{"refund" "extension"})

(defn all
  ([order-id]
   (select resolutions
           (with sellers
                (fields [:alias :seller_alias]))
           (where {:order_id order-id})
           (order :created_on :ASC)))
  ([order-id user-id]
   (select resolutions
           (with sellers
                 (fields :alias))
           (where {:order_id order-id :user_id user-id})
           (order :created_on :ASC))))

(defn all-sales [order-id seller-id]
  (select resolutions
          (with users
                (fields :alias))
          (where {:order_id order-id :seller_id seller-id})
          (order :created_on :ASC)))

(defn extension [id days order_id res]
  (transaction
      (update resolutions
              (set-fields res)
              (where {:id id}))
      (update orders
              (set-fields {:auto_finalize (raw (str "(auto_finalize + interval '" days  " days')"))})
              (where {:id order_id}))))

(defn refund [id user_id seller_id order_id percent res]
  (let [{amount :btc_amount} (update escrow (set-fields {:status "done" :updated_on (raw "now()")}) (where {:order_id order_id :from user_id :status "hold"}))
        user-amount (* amount (/ percent 100))
        seller-amount (- amount user-amount)
        user-audit {:amount user-amount :user_id user_id :role "refund"}
        seller-audit {:amount seller-amount :user_id seller_id :role "refund"}]
    (util/update-session user_id :orders :sales)
    (util/update-session seller_id :orders :sales)
    (if amount
      (transaction
        (update resolutions
                (set-fields res)
                (where {:id id}))
        (insert audits (values [user-audit seller-audit]))
        (update users (set-fields {:btc (raw (str "btc + " user-amount))}) (where {:id user_id}))
        (update users (set-fields {:btc (raw (str "btc + " seller-amount))}) (where {:id seller_id}))
        (insert order-audit (values {:status 5 :order_id order_id :user_id user_id}))
        (update orders (set-fields {:status 5 :finalized true :updated_on (raw "now()")})
                (where {:user_id user_id :finalized false :id order_id}))))))

(defn accept [id user-id]
  (let [res (first (select resolutions (where {:id id :applied false})))] ;;added a flag to see if the resolution was used
      (let [values (if (= (:seller_id res) user-id) {:seller_accepted true} {})
            values (if (= (:user_id res) user-id) (assoc values :user_accepted true) values)]
        (if (or
              (and (:user_accepted values) (:seller_accepted res))
              (and (:user_accepted res) (:seller_accepted values))) ;;check to see if everyone wants this resolution
          (do
            (update resolutions (set-fields {:applied true}) (where {:id id}))
            (if (= (:action res) "extension")
              (extension id (:value res) (:order_id res) values)
              (refund id (:user_id res) (:seller_id res) (:order_id res) (:value res) values)))))))

(defn store! [resolution]
  (insert resolutions (values resolution)))

(defn prep [{:keys [action extension refund content]} order-id user-id]
  "prepares content for the resolutions table,
  takes in a map with an action, extension, refund and message"
  (let [order (first (select orders (where {:id order-id})))
        seller-id (:seller_id order)
        buyer-id (:user_id order)]
      (let [res {:from user-id
                 :applied false
                 :content content
                 :seller_id seller-id
                 :user_id buyer-id
                 :user_accepted (= user-id buyer-id)
                 :seller_accepted (= user-id seller-id)
                 :action (if (contains? actions action) action)
                 :value (if (= action "refund") refund extension)
                 :order_id order-id}]
        (if (nil? (:value res)) (dissoc res :value) res))))

(defn add! [slug order-id user-id]
  (let [resolution (prep slug order-id user-id)
        check (if (= "refund" (:action resolution))
                (v/resolution-refund-validator resolution) (v/resolution-extension-validator resolution))]
        (if (empty? check)
          (store! resolution)
          {:errors check})))
