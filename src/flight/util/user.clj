(ns flight.util.user
  (:refer-clojure :exclude [update])
  (:use
   [flight.db.core]
   [korma.core])
  (:require
   [flight.cache :as cache]
   [flight.util.session :as session]))

(defmacro session! [key func]
  `(let [value# (session/get ~key)]
    (if (nil? value#)
      (let [value# ~func]
        (session/put! ~key value#)
        value#)
      value#)))

(defn current []
  (session! :user
            (if (nil? (session/get :user_id))
              {:currency_id 26}
              (-> (select users (with currency (fields [:key :currency_key] [:symbol :currency_symbol]))
                          (where {:id (session/get :user_id)})) first (dissoc :salt :pass)))))

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
