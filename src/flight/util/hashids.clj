;;This is a thin wrapper around hashidsJava
(ns flight.util.hashids
  (:import (hashids Hashids))
  (:require
    [schema.core :as s]))

;;The salt was generated using java.secure.SecureRandom
(def ^:private salt "w~nwvJxext~PYqj|R`w3m0&c6pYE+a7jkGH{mj")
(def ^:private ticket-salt "Jx99KmieCX98uM8/nQPivIN0kxfSgjpL")
(def ^:private minlength 8)
(def ^:private alphabet "0123456789abcdefghijklmnpqrstuvwxyz")

(defonce h (Hashids. salt minlength alphabet))
(defonce t (Hashids. ticket-salt minlength alphabet))

(defn encrypt [n]
  (.encrypt h n))

(defn encrypt-ticket-id [n]
  (.encrypt t n))

(defn decrypt [s]
  (let [nums (vec (.decrypt h (name s)))];;handles strings and keywords
    (if (> (count nums) 1)
      nums
      (first nums))))

(defn decrypt-ticket-id [s]
  (let [nums (vec (.decrypt t (name s)))];;handles strings and keywords
    (if (> (count nums) 1)
      nums
      (first nums))))

(s/defn Hashid :- Long
  [h :- String]
  (java.lang.Long. (decrypt h)))
