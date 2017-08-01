(ns flight.layout.filters
  (:require
    [selmer.filters :refer :all]
    [hiccup.core :refer [html]]
    [flight.util.core :as util]))

(defn paginate [page maxpage]
  (loop [c 1 o [page]]
    (if (or (> c 5) (>= (count o) 5))
      o
      (let [pageup (+ page c)
            pagedown (- page c)]
        (recur (inc c) (concat (when (> pagedown 0) [pagedown]) o (when (<= pageup maxpage) [pageup])))))))

(add-filter! :pagination
             (fn [x]
               (let [page (or (:page x) 1)
                     m (:max x)
                     params (or (:params x) {})
                     page-param (or (:page-param x) :page)
                     pages (paginate page m)
                     url (:url x)]
                 [:safe (html
                   [:ul.pagination
                    [:li [:a {:href (str url "?" (util/params (assoc params page-param 1)))} "&laquo;"]]
                    (map #(-> [:li (if (= page %) [:strong.selected %] [:a {:href (str url "?" (util/params (assoc params page-param %)))} %])]) pages)
                    [:li [:a {:href (str url "?" (util/params (assoc params page-param m)))} "&raquo;"]]
                    ])])))
