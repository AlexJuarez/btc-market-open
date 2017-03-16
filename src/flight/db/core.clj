(ns flight.db.core
  (:refer-clojure :exclude [update])
  (:use [korma.core]
        [flight.db.helpers])
  (:require [korma.db :refer [create-db default-connection]]
            [korma.core :refer :all]
            [flight.env :refer [env]]
            [mount.core :as mount]))

(mount/defstate db
  :start (default-connection (create-db (env :dbspec))))

(declare
  audits
  bookmarks
  category
  currency
  escrow
  exchange
  fans
  fee
  feedback
  images
  listings
  messages
  modresolutions
  modvotes
  order-audit
  order-form
  orders
  postage
  posts
  region
  reports
  resolutions
  reviews
  sellers
  senders
  ships-to
  users
  wallets
  withdrawals)

(defentity audits
           (table :audit)
           (belongs-to users))

(defentity bookmarks
           (table :bookmark)
           (belongs-to listings)
           (belongs-to users))

(defentity category
           (has-many listings)
           (table :category))

(defentity currency
           (has-many listings)
           (table :currency))

(defentity escrow
           (belongs-to order))

(defentity exchange
           (table :exchangerate))

(defentity fans
           (table :fan)
           (belongs-to users {:fk :leader_id}))

(defentity fees
           (belongs-to order)
           (table :fee))

(defentity feedback
           (belongs-to users))

(defentity images
           (table :image)
           (belongs-to users))

(defentity listings
           (table :listing)
           (has-many ships-to)
           (belongs-to users)
           (belongs-to category)
           (belongs-to currency)
           (has-many reviews)
           (has-one images))

(defentity messages
           (table :message)
           (belongs-to senders {:fk :sender_id})
           (belongs-to users))

(defentity modresolutions
           (table :modresolution)
           (belongs-to users))

(defentity modvotes
           (table :modvote)
           (belongs-to users))

(defentity order-audit
           (table :orderaudit)
           (belongs-to users)
           (belongs-to orders))

(defentity order-form
           (table :order-form))

(defentity orders
           (table :order)
           (belongs-to sellers {:fk :seller_id})
           (belongs-to users)
           (has-one escrow)
           (belongs-to currency)
           (belongs-to listings)
           (belongs-to postage))

(defentity postage
           (belongs-to users)
           (belongs-to currency))

(defentity posts
           (table :post)
           (belongs-to users))

(defentity region
           (has-many users))

(defentity reports
           (table :report)
           (belongs-to users))

(defentity resolutions
           (table :resolution)
           (belongs-to users)
           (belongs-to sellers {:fk :seller_id})
           (belongs-to orders))

(defentity reviews
           (table :review)
           (belongs-to users)
           (belongs-to listings)
           (belongs-to orders))

(defentity sellers
           (table :user))

(defentity senders
           (table :user))

(defentity ships-to
           (belongs-to region)
           (belongs-to listings)
           (table :ships_to))

(defentity users
           (table :user)
           (has-many orders)
           (has-many reviews)
           (has-many listings)
           (has-many messages)
           (has-many images)
           (has-many wallets)
           (has-many postage)
           (has-many fans)
           (has-many posts)
           (has-many bookmarks)
           (belongs-to currency))

(defentity wallets
           (table :wallet)
           (belongs-to users))

(defentity withdrawals
           (table :withdrawal)
           (belongs-to users))
