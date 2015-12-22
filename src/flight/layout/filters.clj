(ns flight.layout.filters
  (:require [clojure.string :as s]
            [flight.util.session :as session]
            [flight.cache :as cache]
            [flight.models.region :as regions]
            [flight.util.core :as util])
  (:use selmer.filters
        hiccup.core))

(add-filter! :empty? (fn [x]
                       (if (string? x) (s/blank? x) (empty? x))))

(add-filter! :rating (fn [x]
                       (int (* (/ x 5.0) 100))))

(add-filter! :count-cart
             (fn [x]
               (:quantity ((session/get :cart) x))))

(add-filter! :postage-cart
             (fn [x]
               (:postage ((session/get :cart) x))))

(add-filter! :freshness
             (fn [x]
               (let [diff (/ (Math/abs (- (.getTime x) (.getTime (java.util.Date.)))) 1000)
                     day (* 24 60 60)
                     days (/ diff day)
                     months (/ diff (* day 30))]
                 (cond
                  (< diff day) "today"
                  (< diff (* 30 day)) (if (= (int days) 1) "yesterday" (str (int days) " days ago"))
                  :else (if (= months 1) "1 month ago" (str (int months) " months ago"))
                  )
                 )))

(defn paginate [page maxpage]
  (loop [c 1 o [page]]
    (if (or (> c 5) (>= (count o) 5))
      o
      (let [pageup (+ page c)
            pagedown (- page c)]
        (recur (inc c) (concat (when (> pagedown 0) [pagedown]) o (when (<= pageup maxpage) [pageup])))))))

(add-filter! :pagination
             (fn [x]
               (let [page (if (nil? (:page x)) 1 (:page x))
                     m (:max x)
                     params (or (:params x) {})
                     pages (paginate page m)
                     url (:url x)]
                 [:safe (html
                   [:ul.pagination
                    [:li [:a {:href (str url "?" (util/params (assoc params :page 1)))} "&laquo;"]]
                    (map #(-> [:li (if (= page %) [:strong.selected %] [:a {:href (str url "?" (util/params (assoc params :page %)))} %])]) pages)
                    [:li [:a {:href (str url "?" (util/params (assoc params :page m)))} "&raquo;"]]
                    ])])))
                    ;;[:li [:a {:href (str url "?page=")} ]]]))))

(defn header [tree params id]
  [:a.header (merge (when (= id (:id tree)) {:class "active"}) {:href (str "/category/" (:id tree) params)})
   [:span.category (:name tree) " " [:span.count  (str "(" (:count tree) ")")]]])

(defn render-tree [tree params id]
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

(add-filter! :status
             (fn [x]
               (let [status ["processing" "shipping" "in resolution" "finalized" "canceled" "refunded"]]
                 (status x))))

(defn region [region_id]
  (let [regions (cache/cache! "regions_map" (into {} (map #(vector (:id %) (:name %)) (regions/all))))]
                 (regions region_id)))

(add-filter! :region region)

(add-filter! :regions (fn [regions]
                        (s/join ", " (map #(region %) regions))))
