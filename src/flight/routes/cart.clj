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
    [flight.util.cart :as cart]
    [flight.util.core :as util
     :refer               [user-id]]
    [flight.routes.cart.middleware :refer [consolidate-cart]]
    [schema.core :as s]))

(defn render-cart [template & params]
  (let [listings  (cart/listings)
        btc-total (cart/total 1)
        total     (cart/total)]
    (layout/render template
                   {:total     total
                    :btc_total btc-total
                    :listings  listings}
                   (first params))))

(defn cart-checkout-validator [{:keys [address pin]}]
  (let [btc-total (cart/total 1)]
    (if (> btc-total (:btc (util/current-user)))
      (error/register! :total "you lack the nessary funds")
      (orders/add! (cart/cart) (cart/total 1) address pin (user-id)))))

(defpage cart-checkout
  :template ["cart/checkout.html"]
  :validator cart-checkout-validator
  (fn [slug] (resp/redirect "/orders")))

(defn cart-view
  ([]
    (render-cart "cart/index.html")))

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

(s/defschema Cart
  {:cart                    {s/Keyword {:id                       (s/both Long (s/pred listing/exists? 'exists?))
                                        (s/optional-key :postage) (s/both Long (s/pred postage/exists? 'exists?))
                                        :quantity                 (s/both Long (greater-than? 0))}}
   (s/optional-key :submit) String})

(defn matches-pin [pin]
  (= pin (:pin (util/current-user))))

(s/defschema Checkout
  {:address              (s/both String (not-empty?))
   (s/optional-key :pin) (s/both String (s/pred matches-pin 'matches-pin))})

(defroutes public-routes
  (context
    "/cart" []
    (GET "/" [] (cart-view))
    (POST "/" []
          :middleware [consolidate-cart]
          :form       [cart Cart]
          :no-doc true
          (cart-submit cart))
    (GET "/empty" [] (cart-empty))
    (context "/add/:id" []
             :path-params [id :- Long]
             (GET "/" [] (cart-add id))
             (POST "/" [] :form-params [postage :- Long] (cart-add id postage)))
    (GET "/:id/remove" []
         :path-params [id :- Long] (cart-remove id))))

(defroutes user-routes
  (context
    "/cart" []
    (GET "/checkout" []
         (cart-checkout))
    (POST "/checkout" []
          :form [checkout Checkout]
          (cart-checkout checkout))))
