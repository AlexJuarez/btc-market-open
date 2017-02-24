(ns flight.routes.cart.middleware)

(defn- map-item [item k m]
  (reduce-kv #(assoc-in %1 [%2 k] %3) item m))

(defn- filter-empty [m]
  (into {} (filter (comp not clojure.string/blank? val) m)))

(defn- remove-empty-values [m]
  (into {} (map #(vector (key %) (filter-empty (val %))) m)))

(defn- create-items [{:keys [postage quantity]}]
  (-> {}
      (map-item "postage" postage)
      (map-item "quantity" quantity)
      (map-item "id" (into {} (map #(vector (key %) (key %)) postage)))
      remove-empty-values))

(defn- create-cart-params [params]
  (fn [p]
    (-> (select-keys p ["__anti-forgery-token" "submit"])
        (assoc "cart" (create-items params)))))

(defn consolidate-cart [handler]
  (fn [req]
    (let [req (-> req
                  (update-in [:form-params] (create-cart-params (:params req))))]
      (handler req))))