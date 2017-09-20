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

(defmacro Str [& args]
  (let [forms (take-while number? args)
        body (drop (count forms) args)]
    (condp = (count forms)
      1 (let [[max] forms] `(s/both String (not-empty?) (less-than? ~max) ~@body))
      2 (let [[min max] forms]
          (if (= min 0)
            `(s/both String (in-range? ~min ~max) ~@body)
            `(s/both String (not-empty?) (in-range? ~min ~max) ~@body)))
      `(s/both String (not-empty?) ~@body))))
