(ns flight.validator
  (:use metis.core
        [flight.db.core])
  (:require
   [metis.core :as v]
   [clojure.string :as s]
   [flight.util.btc :as btc]
   [flight.util.core :as util]
   [korma.core :as sql]))

(defn pin-match [map key _]
  (let [pin (:pin (util/current-user))]
    (when-not (or (nil? pin) (= pin (get map key)))
      "You have entered an incorrect pin")))

(defn check-max [map key _]
  (when-let [amount (get map key)]
    (when (and (integer? (get map :max)) (integer? amount) (> amount (get map :max)))
      "the quantity exceeds the max")))

(defn validate-postage [map key _]
  (let [postage (get map key)
        user_id (get map :user_id)]
      (when (or (not (integer? postage)))
          "You need to select a valid postage option")))

(defn check-funds [map key _]
  (let [user-id (get map :user_id)
        funds (or (:btc (first (sql/select users (sql/fields :btc) (sql/where {:id user-id})))) 0)]
    (when-not (>= funds (get map key)) "insufficient funds")))

(v/defvalidator cart-validator
  [:address [:presence]]
  [:total [:check-funds :numericality {:less-than-or-equal-to 2147483647}]]
  [:pin [:pin-match]])

(v/defvalidator resolution-refund-validator
  [:value [:presence :numericality {:greater-than-or-equal-to 0 :less-than-or-equal-to 100}]])

(v/defvalidator resolution-extension-validator
  [:value [:presence :numericality {:greater-than-or-equal-to 0 :less-than-or-equal-to 30}]])

(v/defvalidator modresolution-validator
  [:percent [:presence :numericality {:greater-than-or-equal-to 0 :less-than-or-equal-to 100}]])

(v/defvalidator cart-item-validator
  [:quantity [:numericality {:greater-than-or-equal-to 0 :less-than-or-equal-to 9999} :check-max]])

(v/defvalidator cart-order-validator
  [:quantity [:presence :numericality {:greater-than-or-equal-to 1 :less-than-or-equal-to 9999} :check-max]]
  [:postage [:presence :validate-postage]])
