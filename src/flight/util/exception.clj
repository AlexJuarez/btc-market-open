(ns flight.util.exception
  (:require
   [ring.util.http-response :refer [bad-request internal-server-error]]
   [clojure.walk :refer [postwalk]]
   [clojure.core.match :refer [match]]
   [taoensso.timbre :as log]
   [schema.utils :as su])
  (:import [schema.utils ValidationError NamedError]))

(defn humanize [x]
  (->
   (match
    x
    ['not ['users/exists? value]]
    "Username does not exist."
    ['not ['valid-captcha? value]]
    "The captcha was entered incorrectly."
    ['not ['instance? type value]]
    "the value is invalid"
    :else
    (str x))
    (cons []))
   )

(defn vectorize
  [m]
  (postwalk
   (fn [x]
     (cond
      (seq? x) (vec x)
      :else x))
   m))

(defn transform [message]
  (cond
    (= (symbol "missing-required-key") message) (symbol "is required")
    :else message))

(defn stringify-error
  "Stringifies symbols and validation errors in Schema error, keeping the structure intact."
  [error]
  (postwalk
    (fn [x]
      (cond
        (instance? ValidationError x) (humanize (vectorize (su/validation-error-explain x)))
        (instance? NamedError x) (humanize (vectorize (su/named-error-explain x)))
        :else (transform x)))
    error))

(defn request-validation
  "Creates error response based on Schema error."
  [data]
  (stringify-error (su/error-val data)))
