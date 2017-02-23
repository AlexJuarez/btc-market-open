(ns flight.routes.cart
  (:require
    [flight.routes.helpers :refer :all]
    [compojure.api.sweet :refer :all]
    [flight.layout :as layout]
    [flight.models.postage :as postage]
    [flight.models.listing :as listing]
    [flight.models.currency :as currency]
    [flight.models.order :as orders]
    [ring.util.response :as resp]
    [flight.util.error :as error]
    [flight.validator :as v]
    [flight.util.session :as session]
    [flight.util.core :as util :refer [user-id]]
    [flight.util.cart :as cart]
    [schema.core :as s]
))

(defn cart-add [id &[postage]]
  (cart/add! id postage)
  (resp/redirect "/cart"))

(defn cart-remove [id]
  (cart/remove! id)
  (resp/redirect "/cart"))

(defn cart-get
  [id key]
  (session/get-in [:cart id key]))

(defn prep-postages [postages]
  (apply merge (map #(hash-map (:id %) (:price %)) postages)))

(defn prep-listing [{:keys [price lid] :as listing} postages error-hash]
  (let [quantity (or (cart-get lid :quantity) 0)
        postage (or (postages (cart-get lid :postage)) 0)
        subtotal (* price quantity)
        total (+ subtotal postage)
        errors (or (get error-hash lid) {})]
    (assoc listing :subtotal subtotal :total total :errors errors)))

(defn prep-listings [listings updates]
  (let [postages (apply merge (map #(prep-postages (:postage %)) listings))]
    (map #(prep-listing % postages updates) listings)))

(defn cart-empty []
  (cart/empty!)
  (resp/redirect "/cart"))

(defn cart-update [{:keys [quantity postage]} listings]
  (let [maxes (reduce merge (map #(hash-map (:lid %) (:quantity %)) listings))
        quantities (reduce-kv #(assoc % (util/parse-int %2) {:max (maxes (util/parse-int %2)) :quantity (or (util/parse-int %3) %3)}) {} quantity)
        postages (reduce-kv #(assoc % (util/parse-int %2) {:postage (or (util/parse-int %3) %3)}) {} postage)
        cart-changes (merge-with merge quantities postages)]
    (let [cart (merge-with merge (session/get :cart) cart-changes)
          cart (apply dissoc cart (keep #(if-let [quantity (util/parse-int (:quantity (val %)))] (when (>= 0 quantity) (key %))) cart))]
      (session/put! :cart cart)
      cart)))

(defn get-listings [slug]
  (let [ls (listing/get-in (keys (session/get :cart)))
        cart (cart-update slug ls)
        ls (keep #(if (not (nil? (get cart (:lid %)))) %) ls)
        errors (reduce-kv #(let [e (v/cart-item-validator %3)] (when-not (empty? e) (assoc % %2 e))) {} cart)
        listings (prep-listings ls errors)]
    listings))

(defn cart-view
  ([&[slug]]
   (when (error/empty?)
     (cart/update! (:cart slug)))
   (let [listings (cart/listings)
         btc-total (cart/total 1)
         total (cart/total)]
     (prn listings)
     (prn slug)
     (layout/render "cart/index.html" {:currency_id (:currency_id (util/current-user))
                                       :total total
                                       :btc_total btc-total
                                       :listings listings}))))

(defn cart-submit [{:keys [items address pin submit] :as slug}]
  (if (= "Update Cart" submit)
    (cart-view slug)
    (let [listings (get-listings slug)
          total (reduce + (map #(:total %) listings))
          btc-total (util/convert-price (:currency_id (util/current-user)) 1 total)
          order (orders/add! (session/get :cart) btc-total address pin (user-id))]
      (if (empty? (:errors order))
        (resp/redirect "/orders")
        (layout/render "cart/index.html" {:errors {}
                                          :convert (not (= (:currency_id (util/current-user)) 1))
                                          :total total :btc-total btc-total
                                          :listings listings} order)))))

(defn cart-checkout []

  )

(defn map-item [item k m]
  (reduce-kv #(assoc-in %1 [%2 k] %3) item m))

(defn- filter-empty [m]
  (into {} (filter (comp not clojure.string/blank? val) m)))

(defn remove-empty-values [m]
  (into {} (map #(vector (key %) (filter-empty (val %))) m)))

(defn create-items [{:keys [postage quantity]}]
  (-> {}
      (map-item "postage" postage)
      (map-item "quantity" quantity)
      (map-item "id" (into {} (map #(vector (key %) (key %)) postage)))
      remove-empty-values))

(defn create-cart-params [params]
  (fn [p]
    (-> (select-keys p ["__anti-forgery-token" "submit"])
        (assoc "cart" (create-items params))
        )))

(defn consolidate-cart [handler]
  (fn [req]
    (let [req (-> req
                      (update-in [:form-params] (create-cart-params (:params req))))]
    (handler req))))

(s/defschema Cart
  {:cart {s/Keyword {
    :id (s/both Long (s/pred listing/exists? 'exists?))
    (s/optional-key :postage) (s/both Long (s/pred postage/exists? 'exists?))
    :quantity (s/both Long (greater-than? 0))}}
    (s/optional-key :submit) String})

(defroutes cart-routes
  (context
   "/cart" []
   (GET "/" [] (cart-view))
   (POST "/" []
         :middleware [consolidate-cart]
         :form [cart Cart]
         (cart-submit cart))
   (GET "/checkout" [] (cart-checkout))
   (GET "/empty" [] (cart-empty))
   (context "/add/:id" []
            :path-params [id :- Long]
            (GET "/" [] (cart-add id))
            (POST "/" [] :form-params [postage :- Long] (cart-add id postage)))
   (GET "/:id/remove" []
         :path-params [id :- Long] (cart-remove id))))
