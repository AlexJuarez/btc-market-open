(ns flight.config
  (:require [selmer.parser :as parser]
            [flight.dev-middleware :refer [wrap-dev]]
            [flight.db.fixtures :refer [load-fixtures]]
            [flight.env :refer [env]]
            [clojure.tools.logging :as log]
            [io.aviso.logging :refer [install-pretty-logging install-uncaught-exception-handler]]))

(def defaults
  {:init
   (fn []
     (install-pretty-logging)
     (install-uncaught-exception-handler)
     (parser/cache-off!)
     (load-fixtures)
     (log/info "-=[flight started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "-=[flight has shut down successfully]=-"))
   :middleware wrap-dev})
