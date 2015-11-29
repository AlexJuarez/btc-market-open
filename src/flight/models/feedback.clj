(ns flight.models.feedback
  (:refer-clojure :exclude [count get])
  (:use [korma.db :only (transaction)]
        [korma.core]
        [flight.db.core])
  (:require
   [flight.validator :as v]))

(defn all []
  (select feedback
          (with users
                (fields :alias))
          (where {:read false})))

(defn get [id]
  (first (select feedback
                 (with users
                       (fields :alias))
                 (where {:id id}))))

(defn prep [{:keys [subject content]} user-id]
  {:subject subject
   :content content
   :user_id user-id})

(defn add! [message user-id]
  (let [message (prep message user-id)
        check (v/support-validator message)]
    (if (empty? check)
      (insert feedback (values message))
      {:errors check})))


(defn add-response! [id slug user-id]
  (let [ticket (get id)
        prepped {:subject (str "RE: " (:subject ticket))
                 :content (:content slug)
                 :user_id (:user_id ticket)
                 :sender_id user-id
                 :feedback_id id}
        check (v/support-validator prepped)]
    (if (empty? check)
      (transaction
       (insert messages
               (values prepped)))
       {:errors check}
      )))
