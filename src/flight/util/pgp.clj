(ns flight.util.pgp
  (:require [taoensso.timbre :as log])
  (:import
    org.bouncycastle.jce.provider.BouncyCastleProvider
    (org.bouncycastle.bcpg ArmoredOutputStream)
    (org.bouncycastle.openpgp PGPLiteralData PGPLiteralDataGenerator PGPEncryptedDataGenerator PGPEncryptedData PGPObjectFactory PGPPublicKey PGPPublicKeyRing PGPUtil)))

(defn get-key-ring [s]
  (try
    (let [factory (-> (.getBytes s "UTF-8") java.io.ByteArrayInputStream. PGPUtil/getDecoderStream PGPObjectFactory.)]
      (.nextObject factory))
      (catch Exception ex
        (log/error "Invalid pgp public key"))))

(defn get-encryption-key [key-ring]
  (when key-ring
    (let [rings (.getPublicKeys key-ring)]
      (if (.hasNext rings)
        (loop [ring (.next rings)]
          (if (or (.isEncryptionKey ring) (not (.hasNext rings)))
            ring
            (recur (.next rings))))))))

(def ^:private provider "BC")
(def ^:private buffer-size 4096)
(def ^:private algorithm PGPEncryptedData/AES_256)

(defn get-id [asc]
  (.toUpperCase (apply str
         (take-last 8
               (-> (get-key-ring asc) (get-encryption-key) (.getKeyID) (java.lang.Long/toHexString))))))

(defn encode [public-key secret]
  (try
    (java.security.Security/addProvider (BouncyCastleProvider.))
    (let [key-ring (get-key-ring public-key)
          secret (.getBytes secret)
          public-key (get-encryption-key key-ring)
          output (java.io.ByteArrayOutputStream. buffer-size)
          armored-output (ArmoredOutputStream. output)
          encryptGen (PGPEncryptedDataGenerator. algorithm (java.security.SecureRandom.) provider)]
      (.addMethod encryptGen public-key)
      (let [encryptedout (.open encryptGen armored-output (byte-array buffer-size))
            finalout (.open (PGPLiteralDataGenerator.)
                            encryptedout
                            PGPLiteralDataGenerator/UTF8
                            PGPLiteralData/CONSOLE
                            (count secret)
                            (java.util.Date.))]
        (do (.write finalout secret) (.close finalout) (.close encryptedout) (.close armored-output) (.close output) (str output))))
  (catch Exception ex
    (log/error ex "Encoding failed")
    "the encoding has failed")))
