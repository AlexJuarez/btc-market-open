(ns flight.routes.helpers
  (:require
    [flight.util.session :as session]
    [flight.util.hashids :as hashids]
    [flight.util.core :as util :refer [user-id]]
    [flight.models.image :as image]
    [flight.models.report :as report]
    [image-resizer.core :as resizer]
    [image-resizer.format :as format]
    [flight.util.image :refer [resource-path upload-file save-file]]
    [clojure.string :as string]
    [taoensso.timbre :as log]
    [schema.core :as s]
    [flight.env :refer [env]]
    [ring.util.response :as resp]))

(defn is-user-logged-in? []
  (and
   (not (nil? (session/get :user_id)))
   (session/get :authed)))

(defn convert-order-price [{:keys [price postage_price postage_currency currency_id quantity] :as order}]
  (when order
    (let [price (util/convert-currency order)
          postage (util/convert-currency postage_currency postage_price)
          total (+ (* price quantity) postage)]
    (-> order (assoc :price price
                     :total total
                     :postage_price postage)))))

(defn encrypt-id [m]
  (when m
    (assoc m :id (hashids/encrypt (:id m)))))

(defn encrypt-ids [l]
  (map encrypt-id l))

(defn estimate-refund [resolutions {:keys [total]}]
  (map #(if (= (:action %) "refund")
            (assoc % :est (* (/ (:value %) 100) total))
          %
         ) resolutions))

(defn parse-image [image_id image]
  (if (or (nil? image) (= 0 (:size image)))
    image_id
    (if (and (< (:size image) 800000) (not (empty? (re-find #"jpg|jpeg" (string/lower-case (:filename image))))))
      (let [image_id (:id (image/add! (user-id)))]
        (try
          (do
            (upload-file (assoc image :filename (str image_id ".jpg")))
            (save-file (resizer/resize-and-crop (clojure.java.io/file (str (resource-path) "/" image_id ".jpg")) 400 300) (str (resource-path) "/" image_id "_max.jpg"))
            (save-file (resizer/resize-and-crop (clojure.java.io/file (str (resource-path) "/" image_id ".jpg")) 180 135) (str (resource-path) "/" image_id "_thumb.jpg"))
            (clojure.java.io/delete-file (str (resource-path) "/" image_id ".jpg")))
          (catch Exception ex
            (log/error ex (str "File upload failed for image " image_id))))
          image_id))))

(defn report-add [object-id user-id table referer]
  (report/add! object-id user-id table)
  (resp/redirect referer))

(defn report-remove [object-id user-id table referer]
  (report/remove! object-id user-id table)
  (resp/redirect referer))

(defn not-empty? []
  (s/pred (comp not empty?) '(not empty?)))

(defn greater-than? [min]
  (s/pred #(<= min (if (number? %) % (count %))) (list 'greater-than? min)))

(defn less-than? [max]
  (s/pred #(<= (if (number? %) % (count %)) max) (list 'less-than? max)))

(defn in-range?
  [min & [max]]
  (if (nil? max)
    (greater-than? min)
    (s/both (greater-than? min) (less-than? max))))

(defn is-alphanumeric?
  []
  (s/pred #(not (nil? (re-matches #"[A-Za-z0-9]+" %))) 'is-alphanumeric?))
