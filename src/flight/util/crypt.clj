(ns flight.util.crypt
  "Simple functions for hashing strings and comparing them. Typically used for storing passwords."
  (:refer-clojure :exclude [compare])
  (:require [clojurewerkz.scrypt.core :as sc]))

(defn encrypt
  "Encrypts a string value using scrypt.
   Arguments are:

   raw (string): a string to encrypt
   :n (integer): CPU cost parameter (default is 16384)
   :r (integer): RAM cost parameter (default is 8)
   :p (integer): parallelism parameter (default is 1)

   The output of SCryptUtil.scrypt is a string in the modified MCF format:

   $s0$params$salt$key

   s0     - version 0 of the format with 128-bit salt and 256-bit derived key
   params - 32-bit hex integer containing log2(N) (16 bits), r (8 bits), and p (8 bits)
   salt   - base64-encoded salt
   key    - base64-encoded derived key"
    [raw & {:keys [n r p]
             :or {n 16384 r 8 p 1}}]
  (sc/encrypt raw n r p))

(defn compare
  "Compare a raw string with an already encrypted string"
  [raw encrypted]
  (boolean
   (if (and raw encrypted)
    (sc/verify raw encrypted))))
