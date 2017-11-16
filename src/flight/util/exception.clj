(ns flight.util.exception
  (:require
    [ring.util.http-response :refer [bad-request internal-server-error]]
    [clojure.walk :refer [postwalk]]
    [clojure.core.match :refer [match]]
    [clojure.tools.logging :as log]
    [schema.utils :as su])
  (:import [schema.utils ValidationError NamedError]))

(defn humanize [x]
  (->
    (match
      x
      ['not ['exists? value]]
      "entered value does not exist"
      ['not [['exists? type] value]]
      (str type " does not exist")
      ['not [['verified? type] value]]
      (str "Your " type " needs to be verified")
      ['not [['taken? type] value]]
      (str "this " type " is already taken")
      ['not [['match? type] value]]
      (str "your " type " do not match")
      ['not ['valid-captcha? value]]
      "The captcha was entered incorrectly"
      ['not ['instance? type value]]
      "the value is invalid"
      ['not ['is-blank? value]]
      "can not be empty"
      ['not [['greater-than? min] value]]
      (str "needs to be " (if (number? value) "greater than or equal to " "longer than ") min)
      ['not [['less-than? max] value]]
      (str "needs to be "
           (if (number? value) "smaller than " "shorter than or equal to") max)
      ['not ['availible? value]]
      (str value " is not availible")
      ['not [['not 'empty?] value]]
      "cannot be empty"
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
