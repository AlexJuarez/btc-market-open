(ns flight.routes.helpers
  (:require [schema.core :as s]))

(defn greater-than? [min]
  (s/pred #(<= min (if (number? %) % (count %))) (list 'greater-than? min)))

(defn less-than? [max]
  (s/pred #(<= (if (number? %) % (count %)) max) (list 'less-than? max)))

(defn in-range?
  [min & [max]]
  (if (nil? max)
    (greater-than? min)
    (s/both (greater-than? min) (less-than? max))))

(defn is-alphanumeric?
  []
  (s/pred #(not (nil? (re-matches #"[A-Za-z0-9]+" %))) 'is-alphanumeric?))

(defn not-empty?
  []
  (s/pred #(not (clojure.string/blank? %)) 'is-blank?))

(defmacro Str
  ([] `(s/both String (not-empty?)))
  ([max] `(s/both String (not-empty?) (less-than? ~max)))
  ([min max] `(s/both String (not-empty?) (in-range? ~min ~max)))
  ([min max & args] `(s/both String (not-empty?) (in-range? ~min ~max) ~@args)))
