(ns flight.util.user
  (:refer-clojure :exclude [update])
  (:require
    [flight.queries.user :as users]
    [flight.queries.currency :as currency]
    [flight.cache :as cache]
    [flight.util.crypt :as crypt]
    [flight.util.session :as session]))

(defmacro session! [key func]
  `(if-let [val# (session/get ~key)]
     val#
     (let [val# ~func]
       (session/put! ~key val#)
       val#)))

(defn- user-id []
  (session/get :user_id))

(defn current []
  (session! :user
            (if (nil? (user-id))
              {:currency_id (:id (currency/get-by-name "USD"))}
              (users/get-by-id user-id))))

(defn password-matches? [password]
  (let [{pass :pass} (current)]
    (crypt/compare password pass)))

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
             (when sess#
               (cache/set session#
                          (assoc sess# :noir (dissoc (:noir sess#) ~@terms :user)) ttl#))))))))
