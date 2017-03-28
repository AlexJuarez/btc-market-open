(ns flight.layout
  (:require
   [flight.util.error :as error]
   [selmer.parser :as parser]
   [selmer.filters :as filters]
   [markdown.core :refer [md-to-html-string]]
   [liberator.core :refer [defresource]]
   [cheshire.core :refer [encode]]
   [ring.util.http-response :refer [content-type ok]]
   [flight.env :refer [env]]
   [ring.util.anti-forgery :refer [anti-forgery-field]]
   [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])
  (:use flight.layout.filters
        flight.layout.tags
        flight.layout.helpers))

(declare ^:dynamic *identity*)
(declare ^:dynamic *app-context*)
(parser/set-resource-path!  (clojure.java.io/resource "templates"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))

(defn render-template
  "renders the HTML template located relative to resources/templates"
  [template params]
  (parser/render-file
    template
    (assoc
      (apply merge {:errors (error/all)} (get-info) params)
      :page template
      :csrf-token *anti-forgery-token*
      :servlet-context *app-context*)))

(defresource render [template & params]
  :available-media-types ["text/plain" "text/html" "application/json"]
  :handle-ok #(let [media-type (get-in % [:representation :media-type])]
               (condp = media-type
                      "text/html" (render-template template params)
                      "text/plain" (render-template template params)
                      "application/json" (when (env :dev) (encode (apply merge {:errors (error/all)} (get-info) params))))
               ))

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error/raw.html" error-details)})
