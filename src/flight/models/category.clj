(ns flight.models.category
  (:refer-clojure :exclude [get update])
  (:use
    [flight.db.core]
    [korma.core]
    [flight.db.predicates]
    [korma.db :only (transaction)]
    [clojure.string :only (split lower-case)])
  (:require
    [cheshire.core :as jr]
    [flight.cache :as cache]))

(defn get [id]
  (first
    (select category (where {:id id}))))

(defn search [query]
  (select category
          (where {:name [ilike query]})
          (limit 10)))

(defn all
  ([]
    (cache/cache! "categories"
      (select category (order :id :ASC))))
  ([cache?]
    (select category (order :id :ASC))))

(defn- search-tree [query]
  (select
   [(subselect listings
               (where {:public true :quantity [> 0] :title [ilike query]})) :l2]
   (fields [:l2.category_id :id])
   (aggregate (count :*) :count)
   (group :l2.category_id)
   (order :l2.category_id :ASC)))

(defn all-search [query]
  (let [cats (apply merge (map #(hash-map (:id %) (assoc % :count 0)) (all)))]
    (->>
     (search-tree query)
     (map #(hash-map (:id %) (assoc (cats (:id %)) :count (:count %))))
     (apply merge)
     (merge cats)
     (vals)
     (sort-by :id))))

;;cache the tree

(defn walk-tree [list parent]
  (if-let [curr (first list)]
    (let [{n :name c :count p :parent id :id gt :gt lte :lte} curr]
      (if (= parent p)
        (flatten (conj [{:name n :count c :gt gt :lte lte :id id :children (walk-tree (next list) id)}] (walk-tree (next list) parent)))
        (if-not (= id parent) (walk-tree (next list) parent))))
    []))

(defn tally-count [tree]
  (if-let [children (:children tree)]
    (assoc tree :count (reduce + (:count tree) (map #(:count (tally-count %)) children)) :children (map tally-count children))
    tree))

(defn prune [cid tree]
  (if-let [children (:children tree)]
    (if (and (> cid (:gt tree)) (<= cid (:lte tree)))
      (assoc tree :children (map #(prune cid %) children))
      (dissoc tree :children))
    tree))

(defn prune-count [cid tree]
  (if-let [children (:children tree)]
    (if (or (and (> cid (:gt tree)) (<= cid (:lte tree))) (> (:count tree) 0))
      (assoc tree :children (map #(prune-count cid %) children))
      (dissoc tree :children))
    tree))

(defn public
  ([cid]
    (let [cats (all false)]
      (prune cid (tally-count (first (walk-tree cats 0))))))
  ([cid query]
    (let [cats (all-search query)]
      (->>
       (walk-tree cats 0)
       first
       tally-count
       (prune-count cid)))))

(defn add! [categories]
  (insert category (values categories)))

(defn load-fixture []
  (add! (jr/parse-string (slurp "resources/categoriesTea.json"))))
