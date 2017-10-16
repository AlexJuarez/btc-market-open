(ns flight.routes.helpers
  (:require
    [flight.util.schema]
    [schema.core :as s]
    [compojure.api.sweet :refer :all]
    [compojure.api.common :refer [extract-parameters]]
    [flight.util.message :as message]
    [flight.util.error :as error]
    [taoensso.timbre :as log]
    [flight.layout :as layout]))

(defn page-route [route page Schema]
  (context
    route []
    (GET "/" []
         (page))
    (POST "/" []
          :form [info Schema]
          (page info))))

(defn resolvefn [obj args]
  (if (vector? obj)
    (let [[k v] obj]
      (if (fn? v)
        [k (apply v args)]
        [k v]))
    obj))

(defn apply-fns [lst args]
  (clojure.walk/prewalk
    #(resolvefn % args)
    lst))

(defn update-template [args body]
  (->>
    body
    (map
      #(if (fn? %)
         (apply % args)
         (apply-fns % args)))
    (apply merge)))

(defn prune [obj]
  (if (and (vector? obj) (= (count obj) 2))
    (let [[k v] obj]
      (when-not (nil? v) obj))
    obj))

(defn- render-page [obj]
  (let [template-path (get obj :template-path)
        render (get obj :render layout/render)]
    (fn [& params] (apply render template-path params))))

(defn- page-success [obj]
  (let [success (get obj :success)]
    (fn [] (when success (message/success! success)))))

(defn- page-validator [obj]
  (let [validator (get obj :validator (fn [_]))]
    (fn [& args] (apply validator args))))

(defn- template-params [obj]
  (let [template-body (get obj :template-body ())]
    (fn [& args] (update-template args template-body))))

(defn parse-options [body]
  (let [[params form] (extract-parameters body true)
        [template & template-body] (get params :template)]
    (clojure.walk/prewalk
      prune
      (->
        params
        (dissoc :template)
        (assoc
          :template-path template
          :template-body template-body
          :body (last form)
          )))))

(defn resolve-page [fargs & body]
  (let [options (parse-options body)
        render (render-page options)
        params (template-params options)
        validator (page-validator options)
        success (page-success options)
        {:keys [args body]} options]
    (if
      (= (inc (count args)) (count fargs))
      (let [[slug & r] fargs]
        (validator slug)
        (if (error/empty?)
          (let [result (apply body fargs)]
            (success)
            (log/debug result)
            (if (and result (:body result))
              result
              (render (apply params r) slug)))
          (render (apply params r) slug)))
      (render (apply params fargs)))))

(defmacro defpage [page-name & body]
  `(def
     ~page-name
     (fn [& args#]
       (resolve-page args# ~@body))))
