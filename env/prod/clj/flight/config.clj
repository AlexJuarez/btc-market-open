(ns flight.config
  (:require [selmer.parser :as parser]
            [flight.env :refer [env]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [ring.middleware.gzip :refer [wrap-gzip]]))

(def defaults
  {:init
   (fn []
     (parser/cache-on!)
     (log/merge-config!
       {:level     ((fnil keyword :info) (env :log-level))
        :appenders {:rotor (rotor/rotor-appender
                             {:path (env :log-path)
                              :max-size (* 512 1024)
                              :backlog 10})
                    }})
     (log/info "-=[flight started successfully using the production profile]=-"))
   :stop
   (fn []
     (log/info "-=[flight has shut down successfully]=-"))
   :middleware (fn [handler] (-> handler wrap-gzip))})
