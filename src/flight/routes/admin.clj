(ns flight.routes.admin
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout]
    [flight.models.audit :as audit]
    [flight.models.escrow :as escrow]
    [flight.util.core :as util :refer [user-id]]
    [ring.util.response :as resp]))

(defn admin-page []
  (let [audits (audit/all)
        escrows (escrow/all)]
  (layout/render "admin/index.html" {:audits audits :escrows escrows})))

(defroutes admin-routes
  (GET "/" [] (admin-page)))
