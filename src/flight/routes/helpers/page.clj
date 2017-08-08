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
        [k (v args)]
        [k v]))
    obj))

(defn apply-fns [lst args]
  (clojure.walk/prewalk
    #(resolvefn % args)
    lst))

(defn update-template [args body]
  (map #(apply-fns % args) body))

(defmacro
  ^{:doc
    "Creates a page function, the macro expects page-name, key value options followed by a body
    to execute on success.
    (defpage test-page
    :template [\"test-page.html\" {:hello \"world\"}]
    :validator (fn [slug] ... )
    :success \"Success message to display\")

    The output of this example would be the same as:
    (defn test-page
    ([] (layout/render \"test-page.html\" {:hello \"world\"}))
    ([slug]
    (validator slug)
    (when (error/empty?)
    (do (...))
    (layout/render \"test-page.html\" slug {:hello \"world\"}))))

    ### options:

    - **:template**                 Define a template-path & args.
    - **:validator**                Define a custom slug validator.
    - **:success**                  Define a message to show on success.
    "
    }
  defpage [page-name & body]
  (let [[params form] (extract-parameters body true)
        [template & template-body] (get params :template)
        args (get params :args)
        validator `(get ~params :validator)
        success `(get ~params :success)
        redirect `(get ~params :redirect)]
    `(defn ~page-name
       ([~@`~args]
         (layout/render ~template (update-template ~@args (list ~@template-body))))
       ([slug# ~@`~args]
        (when-not (nil? ~validator) (~validator slug#))
        (if (error/empty?)
          (do
            (when-not (nil? ~success)
              (message/success! ~success))
            (let [result# (~@form slug#)]
              (log/debug result#)
              (if (not (nil? (get result# :body)))
                result#
                (layout/render ~template slug# (update-template ~@args (list ~@template-body))))))
          (layout/render ~template slug# (update-template ~@args (list ~@template-body))))))))

