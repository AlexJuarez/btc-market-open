(ns flight.layout.filters.core
  (:require
    [hiccup.core :refer [html]]
    [selmer.filters :refer :all]))

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
