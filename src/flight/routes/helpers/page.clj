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
        [template & args] (get params :template)
        validator `(get ~params :validator)
        success `(get ~params :success)
        redirect `(get ~params :redirect)
        form (or form `((fn [& _#] nil)))]
    `(defn ~page-name
       ([]
        (layout/render ~template ~@args))
       ([slug#]
        (when-not (nil? ~validator) (~validator slug#))
        (if (error/empty?)
          (do
            (when-not (nil? ~success)
              (message/success! ~success))
            (let [result# (~@form slug#)]
              (log/debug result#)
              (if (not (nil? (get result# :body)))
                result#
                (layout/render ~template slug#))))
          (layout/render ~template slug# ~@args))))))
