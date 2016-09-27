(ns flight.util.schema
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [compojure.api.middleware :as mw]
   [compojure.api.meta :as m]
   [compojure.api.coerce :as c]
   [flight.util.error :as error]
   [schema.utils :as su]))

(defn src-coerce! [schema key type]
  `(let [value# (keywordize-keys (~key ~m/+compojure-api-request+))]
     (if-let [matchers# (~type (mw/coercion-matchers ~m/+compojure-api-request+))]
       (if-let [matcher# (matchers# ~type)]
         (let [coercer# (~c/memoized-coercer)
               coerce# (coercer# ~schema matcher#)
               result# (coerce# value#)]
           (if (su/error? result#)
             (do
               (error/set-from-validation! result#)
               value#)
             result#))
         value#))
     ))

(defmethod compojure.api.meta/restructure-param :form
  [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :form-params :string)])
      (assoc-in [:swagger :consumes] ["application/x-www-form-urlencoded"])))
