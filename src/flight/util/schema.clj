(ns flight.util.schema
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [compojure.api.middleware :as mw]
   [compojure.api.meta :as m]
   [flight.util.error :as error]
   [schema.utils :as su]))

(defn src-coerce! [schema key type]
  `(let [value# (keywordize-keys (~key ~m/+compojure-api-request+))]
     (if-let [matcher# (~type (mw/get-coercion-matcher-provider ~m/+compojure-api-request+))]
       (let [coerce# (~m/+compojure-api-coercer+ ~schema matcher#)
             result# (coerce# value#)]
         (if (su/error? result#)
           (do
             (error/set-from-validation! result#)
             value#)
           result#))
       value#)))

(defmethod compojure.api.meta/restructure-param :form
  [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :form-params :string)])
      (assoc-in [:parameters :consumes] ["application/x-www-form-urlencoded"])))
