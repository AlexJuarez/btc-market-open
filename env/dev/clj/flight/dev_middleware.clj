(ns flight.dev-middleware
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [selmer.middleware :refer [wrap-error-page]]
            [prone.middleware :refer [wrap-exceptions]]
            [taoensso.timbre :as log]))

(defn log-request [handler]
  (fn [req]
    (do
      (log/debug req)
      (handler req))))

(defn wrap-dev [handler]
  (-> handler
      log-request
      wrap-reload
      wrap-error-page
      wrap-exceptions
      ))
