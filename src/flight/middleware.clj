(ns flight.middleware
  (:require [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [flight.cache :as cache]
            [flight.config :refer [defaults]]
            [flight.env :refer [env]]
            [flight.layout :refer [*identity* *app-context* error-page]]
            [flight.util.error :as error]
            [flight.util.session :as session]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [taoensso.timbre :as log])
  (:import [javax.servlet ServletContext]))

(defn wrap-context [handler]
  (fn [request]
    (binding [*app-context*
              (if-let [context (:servlet-context request)]
                ;; If we're not inside a servlet environment
                ;; (for example when using mock requests), then
                ;; .getContextPath might not exist
                (try (.getContextPath ^ServletContext context)
                     (catch IllegalArgumentException _ context))
                ;; if the context is not specified in the request
                ;; we check if one has been specified in the environment
                ;; instead
                (:app-context env))]
      (handler request))))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t)
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn remove-anti-forgery-token [handler]
  (fn [request]
    (let [request (-> request
                      (update-in [:form-params] dissoc "__anti-forgery-token")
                      (update-in [:multipart-param] dissoc "__anti-forgery-token"))]
    (handler request))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
   (remove-anti-forgery-token
    handler)
   {:error-response
    (error-page
     {:status 403
      :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (wrap-restful-format handler {:formats [:json-kw :transit-json :transit-msgpack]}))

(defn on-error [request response]
  (error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler rule
                     :on-error on-error}))

(defn wrap-identity [handler]
  (fn [request]
    (binding [*identity* (get-in request [:session :identity])]
      (handler request))))

(defn wrap-auth [handler]
  (let [backend (session-backend)]
    (-> handler
        wrap-identity
        (wrap-authentication backend)
        (wrap-authorization backend))))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-auth
      wrap-formats
      wrap-webjars
      session/wrap-flash
      session/wrap-session
      error/wrap-error
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (assoc-in [:session :cookie-name] "session")
            (assoc-in [:session :store] (if (env :couchbase)
                                          (cache/create-couchbase-session-store)
                                          (ttl-memory-store (* 60 30))))))
      wrap-context
      wrap-internal-error))
