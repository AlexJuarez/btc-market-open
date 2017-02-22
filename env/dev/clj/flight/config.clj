(ns flight.config
  (:require [selmer.parser :as parser]
            [flight.dev-middleware :refer [wrap-dev]]
            [flight.db.fixtures :refer [load-fixtures]]
            [flight.env :refer [env]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/merge-config!
       {:level     ((fnil keyword :info) (env :log-level))
        :appenders {:rotor (rotor/rotor-appender
                             {:path (env :log-path)
                              :max-size (* 512 1024)
                              :backlog 10})
                    }})
     (load-fixtures)
     (log/info "-=[flight started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "-=[flight has shut down successfully]=-"))
   :middleware wrap-dev})
