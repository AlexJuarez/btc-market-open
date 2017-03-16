(ns flight.validator
  (:use metis.core
        [flight.db.core])
  (:require
   [metis.core :as v]
   [clojure.string :as s]
   [flight.util.btc :as btc]
   [flight.util.core :as util]
   [korma.core :as sql]))

(defn get-by-login [login]
  (first (sql/select users
    (sql/where (or {:alias login} {:login (s/lower-case login)})))))

(defn get-by-alias [alias]
  (first (sql/select users
    (sql/where (and (or (= :alias alias) (= :login (s/lower-case alias))) (not (= :id (util/user-id))))))))

(defn login-taken [map key _]
  (when-not (empty? (get-by-login (get map key)))
    "This username is already taken"))

(defn alias-taken [map key _]
  (when-not (empty? (get-by-alias (get map key)))
    "This alias is already taken"))

(defn pin-match [map key _]
  (let [pin (:pin (util/current-user))]
    (when-not (or (nil? pin) (= pin (get map key)))
      "You have entered an incorrect pin")))

(defn validate-btc-address [map key _]
  (when-not (btc/validate (get key map))
    "the btc address is not valid"))

(defn check-amount [map key _]
  (when-not (and (empty? (get key map))
                 (>= (:btc (first (sql/select users (sql/fields :btc) (sql/where {:id (util/user-id)}))))
                     (get key map))
      "user does not have the required funds")))

(defn check-max [map key _]
  (when-let [amount (get map key)]
    (when (and (integer? (get map :max)) (integer? amount) (> amount (get map :max)))
      "the quantity exceeds the max")))

(defn validate-postage [map key _]
  (let [postage (get map key)
        user_id (get map :user_id)]
      (when (or (not (integer? postage)))
          "You need to select a valid postage option")))

(defn in-range [map key options]
  (when-not (and (>= (count (get map key)) (:start options)) (<= (count (get map key)) (:end options)))
    (str "This needs to between " (:start options) " and " (:end options))))

(defn check-funds [map key _]
  (let [user-id (get map :user_id)
        funds (or (:btc (first (sql/select users (sql/fields :btc) (sql/where {:id user-id})))) 0)]
    (when-not (>= funds (get map key)) "insufficient funds")))

(v/defvalidator cart-validator
  [:address [:presence]]
  [:total [:check-funds :numericality {:less-than-or-equal-to 2147483647}]]
  [:pin [:pin-match]])

;;Bcypt only looks at the first 73 characters, and saves 60 of them
(v/defvalidator user-validator
  [:login [:presence :login-taken :formatted {:pattern #"[A-Za-z0-9]+" :message "Only alphanumeric characters are valid"} :in-range {:start 3 :end 64}]]
  [:pass [:presence :in-range {:start 8 :end 73} :confirmation {:confirm :confirm}]])

(v/defvalidator user-update-password-validator
  [:pass [:presence :in-range {:start 8 :end 73} :confirmation {:confirm :confirm}]])

(v/defvalidator user-update-validator
  [:alias [:presence :formatted {:pattern #"[A-Za-z0-9]+" :message "Only alphanumeric characters are valid"} :alias-taken :in-range {:start 3 :end 64}]])

(v/defvalidator user-pin-validator
  [:oldpin [:pin-match]]
  [:pin [:presence :in-range {:start 6 :end 60} :confirmation {:confirm :confirmpin}]])

(v/defvalidator user-withdrawal-validator
  [:address [:presence :validate-btc-address]]
  [:amount [:presence :numericality {:greater-than-or-equal-to 0 :less-than-or-equal-to 2147483647} :check-amount]]
  [:pin [:presence :pin-match]])

(v/defvalidator news-validator
  [:content [:presence :length {:is-not-greater-than 6000}]]
  [:subject :length {:is-not-greater-than 100}]
  [:public :accept "true"]
  [:published :accept "true"])

(v/defvalidator support-validator
  [:subject :length {:is-not-greater-than 100}]
  [:content [:presence :length {:is-not-greater-than 6000}]])

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
