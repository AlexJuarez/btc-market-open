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
    [clojure.string :as string]
    [flight.util.core :as util :refer [user-id]]
    [ring.util.response :as resp]))

(def sales-per-page 100)

(defn get-sales [k]
  ((util/session! :sales (order/count-sales (user-id))) k))

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

(defn render-sales [template url status page]
  (let [state ([:new :ship :resolution :finalize] status)
        pagemax (util/page-max (get-sales state) sales-per-page)
        sales (-> (order/sold status (user-id) page sales-per-page) encrypt-ids calculate-amount arbitration)]
     (layout/render template {:sales sales :page page :page-info {:page page :max pagemax :url url}})))

(defn sales-new
  [page]
  (let [state ([:new :ship :resolution :finalize] 0)
        pagemax (util/page-max (get-sales state) sales-per-page)
        sales (-> (order/sold 0 (user-id) page sales-per-page) encrypt-ids calculate-amount arbitration)
        finalized (filter #(:finalized %) sales)
        sales (filter #(not (:finalized %)) sales)]
     (layout/render "sales/new.html" {:sales sales :finalized finalized :page page :paginate {:page page :max pagemax :url "/vendor/sales/new"}})))

(defn sales-shipped
  [page]
  (render-sales "sales/shipped.html" "/vendor/sales/shipped" 1 page))

(defn sales-disputed
  [page]
  (render-sales "sales/disputed.html" "/vendor/sales/resolutions" 2 page))

(defn sales-finailized
  [page]
  (render-sales "sales/finailized.html" "/vendor/sales/past" 3 page))

(defn sales-overview
  [page]
  (let [pagemax (util/page-max (get-sales :total) sales-per-page)
        sales (-> (order/sold (user-id) page sales-per-page) encrypt-ids arbitration)]
     (layout/render "sales/overview.html" {:sales sales :paginate {:page page :max pagemax :url "/vendor/sales"}})))

(defn sales-view
  ([id]
    (let [order (-> (order/get-sale id (user-id)) encrypt-id convert-order-price)
          arbitration (and (= (:status order) 2) (<= (.getTime (:auto_finalize order)) (.getTime (java.util.Date.))))
          resolutions (estimate-refund (resolution/all-sales id (user-id)) order)]
      (layout/render "sales/resolution.html" {:arbitration arbitration
                                              :action "extension" :resolutions resolutions} order)))
  ([id slug]
    (let [res (resolution/add! slug id (user-id))
          order (-> (order/get-sale id (user-id)) encrypt-id convert-order-price)
          resolutions (estimate-refund (resolution/all-sales id (user-id)) order)]
      (layout/render "sales/resolution.html" {:resolutions resolutions} res slug order))))

(defn sales-page
  ([] (sales-overview 1))
  ([{:keys [submit check] :as slug}]
   (let [sales (map #(-> % name hashids/decrypt java.lang.Long.) (keys check))]
     (if (= submit "accept")
       (do (order/update-sales sales (user-id) 1) (resp/redirect "/vendor/sales/new")) ;;state 1 means shipped
       (do (order/reject-sales sales (user-id)) (resp/redirect "/vendor/sales/new"))))))

(defn sales-download [status page]
  (let [state ([:new :ship :resolution :finalize] status)
        pagemax (util/page-max (get-sales state) sales-per-page)
        sales (-> (order/sold status (user-id) page sales-per-page) encrypt-ids calculate-amount arbitration)
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
     (GET "/new" [] (sales-new page))
     (GET "/new/download" [] (sales-download 0 page))
     (GET "/shipped" [] (sales-shipped page))
     (GET "/shipped/download" [] (sales-download 1 page))
     (GET "/resolutions" [] (sales-disputed page))
     (GET "/past" [] (sales-finailized page))
     (POST "/new" {params :params} (sales-page params)))
   (context
    "/sale/" []
     :path-params [id :- String]
     (GET "/" [] (sales-view (Hashid id)))
     (POST "/" {params :params} (sales-view (Hashid id) params))))
