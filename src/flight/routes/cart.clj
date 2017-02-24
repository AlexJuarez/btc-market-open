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

(defn cart-checkout
  ([]
   (let [listings (cart/listings)
         btc-total (cart/total 1)
         total (cart/total)]
     (layout/render "cart/checkout.html" {:total total
                                          :btc_total btc-total
                                          :listings listings}))))

(defn cart-view
  ([]
   (let [listings (cart/listings)
         btc-total (cart/total 1)
         total (cart/total)]
     (layout/render "cart/index.html" {:total total
                                       :btc_total btc-total
                                       :listings listings}))))

(defn cart-add [id &[postage]]
  (cart/add! id postage)
  (resp/redirect "/cart"))

(defn cart-remove [id]
  (cart/remove! id)
   (resp/redirect "/cart"))

(defn cart-empty []
  (cart/empty!)
  (resp/redirect "/cart"))

(defn cart-submit [{:keys [submit] :as slug}]
  (when (error/empty?)
    (cart/update! (:cart slug)))
  (if (and (= "Checkout" submit) (empty? (cart/check)))
    (resp/redirect "/cart/checkout")
    (cart-view)))

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
        (assoc "cart" (create-items params)))))

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
