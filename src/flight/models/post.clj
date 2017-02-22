(ns flight.models.post
  (:refer-clojure :exclude [get update])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core])
  (:require
   [hiccup.util :as hc]))

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
                 (where {:id id}))))

(defn store! [slug]
  (insert posts (values slug)))

(defn prep [{:keys [subject content public published] :as item}]
  (merge item
         {:content (hc/escape-html content)}))

(defn remove! [id user-id]
  (delete posts
          (where {:user_id user-id :id id})))

(defn add! [slug user-id]
  (-> slug prep (assoc :user_id user-id) store!))

(defn publish! [id user-id]
  (update posts
          (set-fields {:published true})
          (where {:user_id user-id :id id})))

(defn update! [slug user-id]
  (let [post (-> slug prep (assoc :updated_on (raw "now()")))]
      (update posts
              (set-fields post)
              (where {:user_id user-id :id (:id slug)}))))
