(ns flight.util.error
  (:refer-clojure :exclude [get empty?])
  (:require [flight.util.exception :refer [request-validation]]))

(declare ^:dynamic *errors*)

(defn put! [k v]
  (swap! *errors* #(assoc-in % [:errors k] v)))

(defn set! [errors]
  (reset! *errors* {:errors (merge (@*errors* :errors) errors)}))

(defn assoc-in! [ks v]
  (swap! *errors* #(assoc-in % (concat [:errors] ks) v)))

(defn update-in! [ks v]
  (swap! *errors* #(update-in % (concat [:errors] ks) v)))

(defn update! [k v]
  (update-in! [k] v))

(defn register! [ks v]
  (let [ks (if (vector? ks) ks [ks])]
    (update-in! ks (fn [curr] (if (nil? curr) [v] (conj curr v))))))

(defn set-from-validation! [v]
  (when-let [errors (request-validation v)]
     (reset! *errors* {:errors (merge (@*errors* :errors) errors)})))

(defn get
  ([k] (get k nil))
  ([k default]
   (clojure.core/get-in @*errors* [:errors k] default)))

(defn all []
  (@*errors* :errors))

(defn empty? []
  (clojure.core/empty? (all)))

(defn wrap-error [handler]
  (fn [request]
    (binding [*errors* (atom {:errors {}})]
      (handler request))))

