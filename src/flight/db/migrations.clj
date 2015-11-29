(ns flight.db.migrations
  (:require
   [taoensso.timbre :as log]
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
  (log/info "checking for new migrations")
  (if (not (actualized?))
    (do
      (log/info "migrations found, applying new migrations")
      (lc/migrate))
    (log/info "no new migrations found")))

(defn migrate-down []
  (lc/rollback))

(defn migrate [args]
  (cond
   (some #{"destroy"} args)
   (do
     (log/info "rolling back all migrations")
     (dotimes [n (count (completed-migrations))]
       (migrate-down)))
   (some #{"rollback"} args)
   (do
     (log/info "rolling back a migration")
     (migrate-down))
   (every? #{"migrate"} args)
   (migrate-up)))
