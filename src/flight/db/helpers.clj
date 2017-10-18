(ns flight.db.helpers
  (:require
   [clojure.java.jdbc :as jdbc]
   [cheshire.core :refer [generate-string parse-string]]
   [clojure.tools.logging :as log]
   [flight.db.protocols]
   [korma.core :as korma]
   [clojure.xml :as xml])
  (:import org.postgresql.util.PGobject
           org.postgresql.jdbc4.Jdbc4Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql BatchUpdateException
            Date
            Timestamp
            PreparedStatement]))

(defn create-pg [type value]
  (doto (PGobject.)
    (.setType type)
    (.setValue value)))

(defn handle-enum [m]
  (into {}
        (for [[k v] m]
          (if (instance? flight.db.protocols.KormaEnum v)
            [k (korma/raw (str "'" (name (:value v)) "'"))]
            [k v]))))

(defn to-date [sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

  Jdbc4Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  java.sql.SQLXML
  (result-set-read-column [v _ _] (xml/parse (.getBinaryStream v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
  (create-pg "jsonb" (generate-string value)))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))

(extend-protocol jdbc/ISQLParameter
  (Class/forName "[Ljava.lang.String;")
  (set-parameter [v ^PreparedStatement stmt ^long i]
    (let [conn (.getConnection stmt)
          meta (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta i)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt i (.createArrayOf conn elem-type v))
        (.setObject stmt i v)))))
