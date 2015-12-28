(ns flight.util.session
  (:refer-clojure :exclude [get get-in remove swap!]))

;;This is bound per request to update the session
(declare ^:dynamic *session*)

(defn put!
  "Associates the key with the given value in the session"
  [k v]
  (clojure.core/swap! *session* assoc k v))

(defn get
  "Get the key's value from the session, returns nil if it doesn't exist."
  ([k] (get k nil))
  ([k default]
    (clojure.core/get @*session* k default)))

(defn get-in
  "Gets the value at the path specified by the vector ks from the session,
  returns nil if it doesn't exist."
  ([ks] (get-in ks nil))
  ([ks default]
    (clojure.core/get-in @*session* ks default)))

(defn clear!
  "Remove all data from the session and start over cleanly."
  []
  (reset! *session* {}))

(defn remove!
  "Remove a key from the session"
  [k]
  (clojure.core/swap! *session* dissoc k))

(defn assoc-in!
  "Associates a value in the session, where ks is a
   sequence of keys and v is the new value and returns
   a new nested structure. If any levels do not exist,
   hash-maps will be created."
  [ks v]
  (clojure.core/swap! *session* #(assoc-in % ks v)))

(defn update-in!
  "'Updates' a value in the session, where ks is a
   sequence of keys and f is a function that will
   take the old value along with any supplied args and return
   the new value. If any levels do not exist, hash-maps
   will be created."
  [ks f & args]
  (clojure.core/swap!
    *session*
    #(apply (partial update-in % ks f) args)))

(defn get!
  "Destructive get from the session. This returns the current value of the key
  and then removes it from the session."
  ([k] (get! k nil))
  ([k default]
   (let [cur (get k default)]
     (remove! k)
     cur)))

(defn get-in!
  "Destructive get from the session. This returns the current value of the path
  specified by the vector ks and then removes it from the session."
  ([ks] (get-in! ks nil))
  ([ks default]
    (let [cur (clojure.core/get-in @*session* ks default)]
      (assoc-in! ks nil)
      cur)))

(defn wrap-session [handler]
  (fn [request]
    (binding [*session* (atom (clojure.core/get-in request [:session] {}))]
      (when-let [resp (handler request)]
        (if (=  (clojure.core/get-in request [:session] {})  @*session*)
          resp
          (if (contains? resp :session)
            (if (nil? (:session resp))
              resp
              (assoc-in resp [:session] @*session*))
            (assoc resp :session @*session*)))))))

;; ## Flash

(declare ^:dynamic *flash*)

(defn flash-put!
  "Store a value that will persist for this request and the next."
  [k v]
  (clojure.core/swap! *flash* assoc-in [:outgoing k] v))

(defn flash-get
  "Retrieve the flash stored value."
  ([k]
     (flash-get k nil))
  ([k not-found]
   (let [in (clojure.core/get-in @*flash* [:incoming k])
         out (clojure.core/get-in @*flash* [:outgoing k])]
     (or out in not-found))))

(defn wrap-flash [handler]
  (fn [request]
    (binding [*flash* (atom {:incoming (:flash request)})]
      (let [resp (handler request)
            outgoing-flash (:outgoing @*flash*)]
        (if (and resp outgoing-flash)
          (assoc resp :flash outgoing-flash)
          resp)))))
