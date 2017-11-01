(ns flight.routes.vendor.sales
  (:require
    [compojure.api.sweet :refer :all]
    [flight.env :refer [env]]
    [flight.routes.helpers :refer :all]
    [flight.layout :as layout]
    [ring.util.response :as resp :refer [content-type response]]
    [flight.util.hashids :as hashids :refer [Hashid]]
    [flight.models.order :as order]
    [flight.models.resolution :as resolution]
    [flight.models.review :as reviews]
    [clojure.string :as string]
    [flight.util.core :as util :refer [user-id]]
    [ring.util.response :as resp]))

(def sales-per-page 100)
(def order-states
  {:new "/vendor/sales/new"
   :ship "/vendor/sales/shipped"
   :resolution "/vendor/sales/resolutions"
   :finalize "/vendor/sales/past"
   :cancelled ""})

(defn get-sales [k]
  (get (util/session! :sales (order/count-sales (user-id))) k 0))

(defn get-order-status [state]
  (->
    order-states
    keys
    vec
    (.indexOf state)))

(defn arbitration [sales]
  (map #(let [arbitration (and (= (:status %) 2)
                               (<= (.getTime (:auto_finalize %)) (.getTime (java.util.Date.))))]
          (assoc % :arbitration arbitration)) sales))

(defn calculate-amount [sales]
  (map #(let [price (util/convert-currency (:currency_id %) (:price %))
              postage-price (util/convert-currency (:postage_currency %) (:postage_price %))
              percent (if (:hedged %) (:hedge_fee %) (env :fee))
              amount (+ (* price (:quantity %)) postage-price)
              fee (* amount percent)]
          (assoc % :arbitration arbitration :amount amount :fee fee)) sales))

(defn- get-orders [state page]
  (->
    (order/sold (mapv get-order-status state) (user-id) page sales-per-page)
    encrypt-ids
    calculate-amount
    arbitration))

(defn- sales-new-params [page]
  (let [pagemax (util/page-max (get-sales :new) sales-per-page)
        orders (get-orders [:new] page)]
    {:sales  (filter :finalized orders)
     :finalized (filter (comp not :finalized) orders)
     :paginate {:page page :max pagemax :url "/vendor/sales/new"}}))

(defn- sales-params [& state]
  (fn [page]
    (let [pagemax (util/page-max (reduce + (map get-sales state)) sales-per-page)
          orders (get-orders state page)]
      {:sales orders
       :page page
       :paginate {:page page :max pagemax :url (get order-states state)}})))

(defpage sales-new-page
  :template ["sales/new.html" sales-new-params]
  :args [:page]
  (fn [{:keys [submit check]}]
    (let [sales (map (comp name hashids/decrypt long) (keys check))]
      (condp = submit
        "accept" (order/update-sales sales (user-id) 1)
        (order/reject-sales sales (user-id))))))

(defpage sales-shipped-page
  :template ["sales/shipped.html" (sales-params :ship)]
  :args [:page])

(defpage sales-disputed-page
  :template ["sales/disputed.html" (sales-params :resolution)]
  :args [:page])

(defpage sales-finalized-page
  :template ["sales/finalized.html" (sales-params :finalize :cancelled)]
  :args [:page])

(defn- get-order [id]
  (->
    (order/get-sale id (user-id))
    encrypt-id
    convert-order-price))

(defn- in-arbitration? [{:keys [status auto_finalize]}]
  (and
    (= status 2)
    (<= (.getTime auto_finalize)
        (.getTime (java.util.Date.)))))

(defn- sale-page-params [id]
  (let [order (get-order id)]
    (merge
      {:review (reviews/for-order id)
       :arbitration (in-arbitration? order)
       :resolutions (estimate-refund (resolution/all-sales id (user-id)) order)}
      order)))

(defpage sale-page
  :template ["sales/resolution.html" sale-page-params]
  :success "Resolution added."
  :args [:id]
  (fn [id slug]
    (resolution/add! slug id (user-id))))

(defn sales-overview
  [page]
  (let [pagemax (util/page-max (get-sales :total) sales-per-page)
        sales (-> (order/sold (user-id) page sales-per-page) encrypt-ids arbitration)]
     (layout/render "sales/overview.html" {:sales sales :paginate {:page page :max pagemax :url "/vendor/sales"}})))

(defn sales-download [status page]
  (let [state ([:new :ship :resolution :finalize] status)
        pagemax (util/page-max (get-sales state) sales-per-page)
        sales (-> (order/sold [status] (user-id) page sales-per-page) encrypt-ids calculate-amount arbitration)
        saleview (string/join "\n" (map #(str "\"" (:id %) "\",\"" (string/replace (:title %) #"[\"]" "\"\"") "\",\""
                                              (string/replace (:postage_title %) #"[\"]" "\"\"") "\",\""
                                              (:quantity %) "\",\""
                                              (:alias %) "\",\""
                                              (:amount %) "\",\""
                                              (:fee %) "\",\""
                                              (string/replace (:address %) #"[\"]" "\"\"") "\"") sales))]
    (-> (response saleview)
        (content-type "text/plain")
        (resp/header "Content-Disposition" (str "attachment;filename=" (util/format-time (java.util.Date. ) "MM-dd-yyyy") "-sales-" (name state) "-" page ".csv")))))

(defroutes vendor-routes
   (context
    "/sales" []
     :query-params [{page :- Long 1}]
     (GET "/" [] (sales-overview page))
     (GET "/new" [] (sales-new-page page))
     (GET "/new/download" [] (sales-download 0 page))
     (GET "/shipped" [] (sales-shipped-page page))
     (GET "/shipped/download" [] (sales-download 1 page))
     (GET "/resolutions" [] (sales-disputed-page page))
     (GET "/past" [] (sales-finalized-page page))
     (POST "/new" {params :params} (sales-new-page params)))
   (context
    "/sale/:id" []
     :path-params [id :- String]
     (GET "/" [] (sale-page (Hashid id)))
     (POST "/" {params :params} (sale-page (Hashid id) params))))
