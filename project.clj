(defproject flight "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[bouncer "1.0.0"]
                 [buddy "1.1.0"]
                 [clj-btc "0.11.2"]
                 [clj-http "2.0.0"];;for reading bitcoins prices from coinbase.com
                 [clojurewerkz/scrypt "1.2.0"]
                 [clojurewerkz/spyglass "1.1.0"];;couchbase interface
                 [com.fzakaria/slf4j-timbre "0.2.1"]
                 [com.mchange/c3p0 "0.9.5.1"] ;;connection pooling
                 [com.taoensso/timbre "4.1.4"]
                 [com.taoensso/tower "3.0.2"]
                 [compojure "1.5.1"]
                 [conman "0.2.7"]
                 [crypto-random "1.2.0"] ;;crypto lib
                 [environ "1.0.1"]
                 [hashobject/hashids "0.2.0"];;for anon hashing
                 [image-resizer "0.1.9"]
                 [korma "0.4.2" :exclusions [c3p0/c3p0]] ;;sql dsl
                 [lobos "1.0.0-beta3"]
                 [markdown-clj "0.9.82"]
                 [metis "0.3.3"];;validator
                 [metosin/compojure-api "1.1.8"]
                 [metosin/ring-http-response "0.6.5"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-swagger "0.22.0"]
                 [metosin/ring-swagger-ui "2.1.3-4"]
                 [migratus "0.8.7"]
                 [mount "0.1.4" :exclusions [ch.qos.logback/logback-classic]]
                 [net.sf.jlue/jlue-core "1.3"];;captcha creation
                 [org.bouncycastle/bcpg-jdk15on "1.50"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.immutant/web "2.1.1" :exclusions [ch.qos.logback/logback-classic]]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"] ;;postgres adapter
                 [org.slf4j/log4j-over-slf4j "1.7.12"]
                 [prismatic/schema "1.0.3"]
                 [ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]
                 [ring-ttl-session "0.1.1"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-defaults "0.2.1"]
                 [selmer "0.9.5"] ;;templating
                 [slingshot "0.12.2"] ;;smarter error handling
                 [to-jdbc-uri "0.2.0"]];;jdbc uri parser

  :min-lein-version "2.5.2"
  :uberjar-name "flight.jar"
  :jvm-opts ["-server"]

  :main flight.core
  :migratus {:store :database}

  :plugins [[lein-ancient "0.6.10"]
            [lein-environ "1.0.1"]
            [org.clojars.punkisdead/lein-cucumber "1.0.4"]]
  :cucumber-feature-paths ["test/features"]
  :profiles
  {:uberjar {:omit-source true
             :env {:production true}
             :aot :all
             :source-paths ["env/prod/clj"]}
   :dev           [:project/dev :profiles/dev]
   :test          [:project/test :profiles/test]
   :project/dev  {:dependencies [[prone "0.8.2"]
                                 [ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.4.0"]
                                 [pjstadig/humane-test-output "0.7.0"]
                                 [clj-webdriver/clj-webdriver "0.6.1"]
                                 [org.apache.httpcomponents/httpcore "4.4"]
                                 [org.clojure/core.cache "0.6.3"]
                                 [mvxcvi/puget "1.0.0"]]

                  :source-paths ["env/dev/clj"]
                  :repl-options {:init-ns flight.core}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]
                  ;;when :nrepl-port is set the application starts the nREPL server on load
                  :env {:dev        true
                        :port       3000
                        :nrepl-port 7000
                        :remote-bitcoin-values "https://api.coinbase.com/v1/currencies/exchange_rates"
                        :dbspec {:classname "org.postgresql.Driver"
                                  :subprotocol "postgresql"
                                  :subname "//localhost/whitecity"
                                  :user "devil"
                                  :password "admin"}
                        :log-level  :trace}}
   :project/test {:env {:test       true
                        :port       3001
                        :nrepl-port 7001
                        :log-level  :trace
                        :dbspec {:classname "org.postgresql.Driver"
                                  :subprotocol "postgresql"
                                  :subname "//localhost/whitecity"
                                  :user "devil"
                                  :password "admin"}}}

   :profiles/dev {}
   :profiles/test {}})
