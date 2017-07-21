(ns flight.util.user
  (:refer-clojure :exclude [update])
  (:use
   [flight.db.core]
   [korma.core])
  (:require
   [flight.cache :as cache]
   [flight.util.session :as session]))

(defmacro session! [key func]
  `(if-let [val# (session/get ~key)]
     val#
     (let [val# ~func]
       (session/put! ~key val#)
       val#)))

(defn- user-id []
  (session/get :user_id))

(defn- get-user [user-id]
  (-> (select* users)
      (with currency (fields [:key :currency_key] [:symbol :currency_symbol]))
      (where {:id user-id})
      select
      first
      (dissoc :pass)))

(defn current []
  (session! :user
            (if (nil? (user-id))
              {:currency_id 26}
              (get-user (user-id)))))

(defmacro update-session
  [user-id & terms]
    `(let [id# (parse-int ~user-id)
           user-id# (session/get :user_id)]
      (if (= id# user-id#)
        (dorun (map session/remove! (list :user ~@terms)))
        (let [user# (first (select users (fields :session) (where {:id id#})))]
          (when (:session user#)
            (let [session# (.toString (:session user#))
                  sess# (cache/get session#)
                  ttl# (* 60 60 10)]
              (if (not (nil? sess#))
                (cache/set session#
                           (assoc sess# :noir (dissoc (:noir sess#) ~@terms :user)) ttl#))))))))
