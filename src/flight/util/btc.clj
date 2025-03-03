(ns flight.util.btc
  (:require
   [flight.env :refer [env]]
   [clj-btc.core :as btc]
   [clojure.tools.logging :as log]))

(defonce digits58 "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(defn address [account]
  (try
    (btc/getaccountaddress :account (str account) :config (env :btcspec))
    (catch Exception ex
      (log/error "Address creation error")
      (throw ex))))

(defn newaddress [account]
  (try
    (btc/getnewaddress :account (str account) :config (env :btcspec))
    (catch Exception ex
      (log/error "Address creation error - new address")
      (throw ex))))

(defn privkey [address]
  (if (string? address)
    (try
      (btc/dumpprivkey :bitcoinaddress address :config (env :btcspec))
      (catch Exception ex
        (log/error "Address private key retrieval failed")
        (throw ex)))))

(defn decode-base58 [s]
  (let [arr (.toByteArray
            (.toBigInteger (reduce #(+ (* 58 %) %2) (map #(bigint (.indexOf digits58 (str %))) s))))]
    (if (> 25 (count arr))
      (-> arr seq (cons (take (- 25 (count arr)) (repeat 0))) byte-array);;25 is the length of a wallet address
      arr
    )))

(defn validate [bc]
  (if-not (empty? bc)
    (let [bcbytes (decode-base58 bc)
          md (java.security.MessageDigest/getInstance "SHA-256")
          hashone (do (.update md (byte-array (drop-last 4 bcbytes))) (.digest md))
          md (java.security.MessageDigest/getInstance "SHA-256")
          hashtwo (do (.update md hashone) (.digest md))]
      (=
        (take-last 4 bcbytes)
        (take 4 hashtwo)))))
