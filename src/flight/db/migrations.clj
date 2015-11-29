(ns flight.db.migrations
  (:require
   [taoensso.timbre :as timbre]
   [lobos.migration :as lm]
   [lobos.core :as lc]
   [flight.env :refer [env]]))

(lc/defcommand pending-migrations []
  (lm/pending-migrations (env :dbspec) nil))

(lc/defcommand completed-migrations []
  (lm/query-migrations-table (env :dbspec) nil))

(defn actualized?
    "checks if there are no pending migrations"
    []
    (empty? (pending-migrations)))

(defn migrate-up []
  (timbre/info "checking for new migrations")
  (if (not (actualized?))
    (do
      (timbre/info "migrations found, applying new migrations")
      (lc/migrate))
    (timbre/info "no new migrations found")))

(defn migrate-down []
  (lc/rollback))

(defn migrate [args]
  (cond
   (some #{"destroy"} args)
   (do
     (timbre/info "rolling back all migrations")
     (dotimes [n (count (completed-migrations))]
       (migrate-down)))
   (some #{"rollback"} args)
   (do
     (timbre/info "rolling back a migration")
     (migrate-down))
   (every? #{"migrate"} args)
   (migrate-up)))
