(ns flight.models.moderate
   (:use [korma.db :only (transaction)]
         [korma.core]
         [flight.db.core])
  (:require
        [flight.validator :as v]
        [flight.util :as util]))

(defn prep [order_id slug user-id]
  (let [order (first (select orders
                 (with sellers (fields :login :alias))
                 (where {:id order_id})))]
    {:user_id user-id
     :buyer_id (:user_id order)
     :seller_id (:seller_id order)
     :order_id (:id order)
     :percent (util/parse-int (:percent slug))
     :content (:content slug)}
    ))

(defn store! [resolution]
  (insert modresolutions (values resolution)))

(defn add! [id slug user-id]
  (let [resolution (prep id slug user-id)
        check (v/modresolution-validator resolution)]
    (if (empty? check)
      (do (store! resolution) nil)
      check)))

(defn get-vote [res user-id]
  (let [res (util/parse-int res)]
    (first
     (select modvotes (where {:modresolution_id res :user_id user-id})))))

(defn voted? [res user-id]
  (not (nil? (get-vote res user-id))))

(defn vote! [res user-id]
  (let [res (util/parse-int res)]
    (transaction
     (update modresolutions
             (set-fields {:votes (raw "votes + 1")})
             (where {:id res}))
     (insert modvotes (values {:modresolution_id res :user_id user-id})))))

(defn remove-vote! [res user-id]
  (when (voted? res user-id)
    (let [res (util/parse-int res)]
      (transaction
       (update modresolutions
               (set-fields {:votes (raw "votes - 1")})
               (where {:id res}))
       (delete modvotes (values {:modresolution_id res :user_id user-id}))))))

(defn refund [id user_id seller_id order_id percent]
  (let [{amount :btc_amount} (update escrow (set-fields {:status "done" :updated_on (raw "now()")}) (where {:order_id order_id :status "hold"}))
        user-amount (* amount (/ percent 100.0))
        seller-amount (- amount user-amount)
        user-audit {:amount user-amount :user_id user_id :role "refund"}
        seller-audit {:amount seller-amount :user_id seller_id :role "refund"}]
    (util/update-session user_id :orders :sales)
    (util/update-session seller_id :orders :sales)
    (if amount
      (transaction
        (insert audits (values [user-audit seller-audit]))
        (update users (set-fields {:btc (raw (str "btc + " user-amount))}) (where {:id user_id}))
        (update users (set-fields {:btc (raw (str "btc + " seller-amount))}) (where {:id seller_id}))
        (insert order-audit (values {:status 5 :order_id order_id :user_id user_id}))
        (update orders (set-fields {:status 5 :finalized true :updated_on (raw "now()")})
                (where {:user_id user_id :finalized false :id order_id}))))))

(defn accept! [id user-id]
  (let [id (util/parse-int id)
        res (first (select modresolutions (where {:id id :applied false})))] ;;added a flag to see if the resolution was used
    (when (not (nil? res))
      (update modresolutions (set-fields {:applied true}) (where {:id id}))
            (refund id (:buyer_id res) (:seller_id res) (:order_id res) (:percent res))
      )))

(defn all [order-id]
  (select modresolutions
          (with users
                (fields :alias))
          (where {:order_id order-id})
          (order :created_on)))
