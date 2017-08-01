(ns flight.layout.filters
  (:require
    [hiccup.core :refer [html]]
    [selmer.filters :as filters]
    [selmer.parser :as parser]))

(defn- message-element [content]
  (when-not (empty? content)
    (when-let [c (-> content :type name str)]
      [:div.message {:class c} (:content content)])))

(filters/add-filter! :format-message (fn [content]
                                       (when-let [elem (message-element content)]
                                         [:safe (html elem)])))

(defn- error-element [content]
  (when-not (empty? content)
    [:div.errors
     (str
       (->> content
            (clojure.string/join ", ")
            clojure.string/capitalize)
       ".")]))

(filters/add-filter! :format-error
                     (fn
                       [content]
                       (when-let [elem (error-element content)]
                         [:safe (html elem)])))

(parser/add-tag! :has-error (fn [args context-map] (when (not (empty? (get-in context-map [:errors (keyword (first args))]))) (str "has-error"))))
