(ns flight.layout.filters
  (:require
    [flight.util.core :as util]
    [selmer.filters :refer :all]
    [hiccup.core :refer [html]]))

(defn- header [tree params id]
  [:a.header (merge (when (= id (:id tree)) {:class "active"}) {:href (str "/category/" (:id tree) params)})
   [:span.category (:name tree) " " [:span.count  (str "(" (:count tree) ")")]]])

(defn- render-tree [tree params id]
  (let [children (:children tree)]
    (if-not (empty? children)
      [:li
       (header tree params id)
       [:ul (map #(render-tree % params id) children)]]
      [:li
       (header tree params id)])))

(add-filter! :render-tree
             (fn [x]
               (let [tree (:tree x)
                     id (:id x)
                     params (if-not (empty? (:params x)) (str "?" (util/params (:params x))))]
                 [:safe (html [:ul {:class "category-tree"} (render-tree tree params id)])])))
