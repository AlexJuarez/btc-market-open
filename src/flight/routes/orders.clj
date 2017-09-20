(ns flight.routes.orders
  (:require
    [compojure.api.sweet :refer :all]
    [flight.routes.helpers :refer :all]
    [flight.layout :as layout]
    [flight.models.order :as order]
    [flight.models.review :as review]
    [flight.util.hashids :as hashids
     :refer                  [Hashid]]
    [flight.models.resolution :as resolution]
    [ring.util.response :as resp]
    [flight.util.core :as util
     :refer               [user-id]]
    [schema.core :as s]
    [flight.access :as access]))

(defn parse-reviews [{:keys [rating content shipped]}]
  (->>
    (keys rating)
    (map #(assoc {} :order_id (Hashid %) :rating (% rating) :content (% content) :shipped (% shipped)))))

(defn orders-page
  ([]
   (let [orders         (order/all (user-id))
         orders         (map
                          #(let [autofinalize (:auto_finalize %)
                                 res
                                 (and (not (nil? autofinalize))
                                      (> 432000000 (- (.getTime autofinalize) (.getTime (java.util.Date.)))))
                                 arbitration
                                 (and (= (:status %) 2) (<= (.getTime autofinalize) (.getTime (java.util.Date.))))]
                             ;;TODO: review resolution stuff
                             (assoc % :resolve res :arbitration arbitration :id (hashids/encrypt (:id %))))
                          orders)
         pending-review (filter #(and (= false (:reviewed %)) (= true (:finalized %))) orders)
         orders         (filter #(< (:status %) 3) orders)]
     (layout/render "orders/index.html"
                    {:orders orders :pending-review pending-review :user-id (user-id)})))
  ([slug]
   (let [raw-reviews (parse-reviews slug)
         reviews   (review/add! raw-reviews (user-id))]
     (resp/redirect "/orders"))))

(defn order-finalize [id]
  (order/finalize id (user-id))
  (resp/redirect "/orders"))

(defn order-cancel [id]
  (order/cancel! id (user-id))
  (resp/redirect (str "/orders")))

(defn order-view [id]
  (let [order       (-> (order/get-order id (user-id)) encrypt-id convert-order-price)
        arbitration (and (= (:status order) 2)
                         (<= (.getTime (:auto_finalize order)) (.getTime (java.util.Date.))))
        resolutions (estimate-refund (resolution/all id (user-id)) order)]
    (layout/render "orders/resolution.html"
                   {:action      "extension"
                    :arbitration arbitration
                    :resolutions resolutions
                    :order       order}
                   order)))

(defn order-add-resolution [id slug]
  (let [res         (resolution/add! slug id (user-id))
        order       (-> (order/get-order id (user-id)) encrypt-id convert-order-price)
        resolutions (estimate-refund (resolution/all id (user-id)) order)]
    (layout/render "orders/resolution.html" {:resolutions resolutions} slug res order)))

(defn order-resolve [id]
  (order/resolution id (user-id))
  (resp/redirect (str "/order/" (hashids/encrypt id))))

(defn valid-ratings? [ratings]
  (every?
    #(let [id (key %) value (val %)] (and (not (nil? (hashids/decrypt id))) (<= value 5) (>= value 0)))
    ratings))

(defn valid-shipped? [shipped]
  (every?
    #(let [id (key %) value (val %)] (and (not (nil? (hashids/decrypt id))) (or (= value "true") (= value "false"))))
    shipped))

(defn valid-content? [contents]
  (every?
    #(let [id (key %) value (val %)] (and (not (nil? (hashids/decrypt id))) (string? value)))
    contents))

(s/defschema Reviews
  {(s/optional-key :rating)  (s/pred valid-ratings? 'valid-ratings?)
   (s/optional-key :shipped) (s/pred valid-shipped? 'valid-shipped?)
   (s/optional-key :content) (s/pred valid-content? 'valid-content?)})

(s/defschema Resolution
  {:action                     (s/enum :extension :refund)
   (s/optional-key :extension) Long
   (s/optional-key :refund)    Long
   (s/optional-key :content)   (Str 0 2000)})

(defroutes user-routes
  (context
    "/orders" []
    :tags ["user"]
    :access-rule access/user-authenticated
    (GET "/" [] (orders-page))
    (POST "/" {params :params}
          (orders-page params)))
  (context
    "/order/:id" []
    :path-params [id :- String]
    :tags ["user"]
    :access-rule access/user-authenticated
    (GET "/resolve" [] (order-resolve (Hashid id)))
    (GET "/cancel" [] (order-cancel (Hashid id)))
    (GET "/" [] (order-view (Hashid id)))
    (POST "/" []
          :form [resolution Resolution]
          (order-add-resolution (Hashid id) resolution))
    (GET "/finalize" [] (order-finalize (Hashid id)))))
