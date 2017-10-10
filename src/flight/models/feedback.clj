(ns flight.models.feedback
  (:refer-clojure :exclude [count get update])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core]))

(defn select-feedback []
  (-> (select* feedback)
      (with users
            (fields :alias))))

(defn all []
  (->
    (select-feedback)
    (where {:read false})
    select))

(defn get [id]
  (->
    (select-feedback)
    (where {:id id})
    select
    first))

(defn prep [{:keys [subject content]} user-id]
  {:subject subject
   :content content
   :user_id user-id})

(defn add! [message user-id]
  (let [message (prep message user-id)]
    (insert feedback (values message))))


(defn add-response! [id slug user-id]
  (let [ticket (get id)
        prepped {:subject (str "RE: " (:subject ticket))
                 :content (:content slug)
                 :user_id (:user_id ticket)
                 :sender_id user-id
                 :feedback_id id}]
    (insert messages
            (values prepped))))
