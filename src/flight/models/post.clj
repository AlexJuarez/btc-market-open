(ns flight.models.post
  (:refer-clojure :exclude [get])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core])
  (:require
   [flight.validator :as v]
   [hiccup.util :as hc]
   [flight.util :as util]))

(def per-page 25)

(defn all [user-id]
  (select posts
          (where {:user_id user-id})))

(defn get-updates [user-id]
  (select posts
          (where {:user_id user-id :public true :published true})))

(defn get-news [user-id]
  (select fans
          (fields :leader_id :post.id :post.public :post.subject :post.created_on)
          (with users
                (fields :alias))
          (join posts (= :post.user_id :leader_id))
          (order :post.created_on :asc)
          (where {:user_id user-id :post.public false :post.published true})
          (limit per-page)))

(defn get [id]
  (first (select posts
                 (with users
                       (fields :alias))
                 (where {:id (util/parse-int id)}))))

(defn store! [slug]
  (insert posts (values slug)))

(defn prep [{:keys [subject content public published]}]
  {:subject subject
   :content (hc/escape-html content)
   :published (= published "true")
   :public (= public "true")})

(defn remove! [id user-id]
  (delete posts
          (where {:user_id user-id :id (util/parse-int id)})))

(defn add! [slug user-id]
  (let [check (v/news-validator slug)]
    (if (empty? check)
      (-> slug prep (assoc :user_id user-id) store!)
      (conj {:errors check} slug))))

(defn publish! [id user-id]
  (update posts
          (set-fields {:published true})
          (where {:user_id user-id :id (util/parse-int id)})))

(defn update! [slug user-id]
  (let [check (v/news-validator slug)
        post (-> slug prep (assoc :updated_on (raw "now()")))]
    (if (empty? check)
      (update posts
              (set-fields post)
              (where {:user_id user-id :id (util/parse-int (:id slug))}))
      (conj {:errors check} slug))))
