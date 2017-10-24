(ns flight.util.user
  (:refer-clojure :exclude [update])
  (:require
    [flight.queries.user :as users]
    [flight.queries.currency :as currency]
    [flight.cache :as cache]
    [flight.util.crypt :as crypt]
    [flight.util.session :as session]
    [flight.util.core :refer [session!]]))

(defn- user-id []
  (session/get :user_id))

(defn current []
  (session! :user
            (if (nil? (user-id))
              {:currency_id (:id (currency/get-by-name "USD"))}
              (users/get-by-id (user-id)))))

(defn password-matches? [password]
  (let [{pass :pass} (current)]
    (crypt/compare password pass)))
