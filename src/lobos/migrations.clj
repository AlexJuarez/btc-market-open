(ns lobos.migrations
  (:refer-clojure
     :exclude [alter drop bigint boolean char double float time])
  (:use (lobos [migration :only [defmigration]] core schema config helpers)))

(defmigration add-currencies-table
  (up [] (create
           (tbl :currency
                (varchar :key 3 :unique :not-null)
                (varchar :name 200)
                (float :hedge_fee)
                (varchar :symbol 10))))
  (down [] (drop (table :currency))))

(defmigration add-regions-table
  (up [] (create
           (table :region
                  (integer :id :auto-inc :primary-key)
                  (varchar :name 64)
                  (timestamp :created_on (default (now))))))
  (down [] (drop (table :region))))

(defmigration add-users-table
  (up [] (create
          (tbl :user
               ;; General Fields
               (varchar :login 64 :unique)
               (check :login_max (< (length :login) 64))
               (check :login_min (> (length :login) 2))
               (varchar :alias 64 :unique)
               (check :alias_max (< (length :alias) 64))
               (check :alias_min (> (length :alias) 2))
               (varchar :pass 128)
               (check :pass_max (< (length :pass) 128))
               (check :pass_min (> (length :pass) 4))

               ;; Utility Fields
               (column :session (data-type :uuid) :unique)
               (timestamp :last_login)

               ;; Login Protection
               (timestamp :last_attempted_login);;Last attempted login
               (integer :login_tries (default 0))

               ;; Privileges
               (boolean :vendor (default false))
               (boolean :admin (default false))
               (boolean :mod (default false))
               (boolean :auth (default false))
               (boolean :banned (default false))
               (boolean :vacation (default false))

               ;; Wallet
               (float :btc (default 0))
               (check :btc (>= :btc 0))
               (refer-to :currency)
               (varchar :wallet 34 :unique)
               (varchar :pin 60)

               ;; Information
               (text :description)
               (check :description (< (length :description) 2000))
               (text :pub_key)
               (check :pub_key (< (length :pub_key) 2000))
               (varchar :pub_key_id 8)

               ;; Stats
               (integer :transactions (default 0))
               (check :transactions (>= :transactions 0))
               (float :rating (default 5.0))
               (check :rating_min (>= :rating 0.0))
               (check :rating_max (<= :rating 5.0))
               (integer :ranking)
               (integer :resolutions (default 0))
               (check :resolutions (>= :resolutions 0))
               (integer :reviewed (default 0))
               (check :reviewed (>= :reviewed 0))
               (integer :fans (default 0))
               (check :fans (>= :fans 0))
               (integer :listings (default 0))
               (check :listings (>= :listings 0))
               (integer :bookmarks (default 0))
               (check :bookmarks (>= :bookmarks 0))
               (integer :region_id [:refer :region :id :on-delete :set-null] (default 1))
              )))
  (down [] (drop (table :user) :cascade)))

(defmigration add-user-wallets-table
  (up [] (create
           (tbl :wallet
                (refer-to :user)
                (varchar :wallet 34 :unique)
                (varchar :privkey 52)
                (timestamp :checked_on))))
  (down [] (drop (table :wallet))))

(defmigration add-wallet-withdrawals-table
  (up [] (create
           (tbl :withdrawal
                (refer-to :user)
                (varchar :address 34)
                (boolean :locked (default false))
                (boolean :processed (default false))
                (float :amount))))
  (down [] (drop (table :withdrawal))))

(defmigration add-feedback-table
  (up [] (create
           (tbl :feedback
                  (boolean :read (default false))
                  (text :content)
                  (check :content (< (length :content) 1000))
                  (varchar :subject 100)
                  (refer-to :user))))
  (down [] (drop (table :feedback))))

(defmigration add-messages-table
  (up [] (create
           (tbl :message
                  (boolean :read (default false))
                  (refer-to :feedback)
                  (text :content)
                  (check :content (< (length :content) 2000))
                  (varchar :subject 100)
                  (check :subject (< (length :subject) 100))
                  (refer-to :user)
                  (integer :sender_id [:refer :user :id :on-delete :set-null]))))
  (down [] (drop (table :message))))

(defmigration add-images-table
  (up [] (create
           (tbl :image
                (refer-to :user)
                (varchar :name 67)
                (check :name (< (length :name) 67))
                )))
  (down [] (drop (table :image))))

(defmigration add-exchange-rate-table
  (up [] (create
           (table :exchangerate
                (integer :from [:refer :currency :id :on-delete :set-null])
                (integer :to [:refer :currency :id :on-delete :set-null])
                (float :value)
                (check :value (>= :value 0))
                (timestamp :updated_on (default (now))))))
  (down [] (drop (table :exchangerate))))

(defmigration add-categories-table
  (up [] (create
           (tbl :category
                (varchar :name 30)
                (integer :count (default 0))
                (integer :lte)
                (integer :gt)
                (integer :parent)
                (check :count (>= :count 0)))))
  (down [] (drop (table :category))))

(defmigration add-listings-table
  (up [] (create
           (tbl :listing
                (boolean :public (default false))
                (boolean :hedged (default false))
                (varchar :title 100)
                (integer :from [:refer :region :id])
                (column :to (data-type "integer[]"))
                (refer-to :user)
                (refer-to :image)
                (float :price :not-null)
                (float :converted_price :not-null)
                (integer :quantity (default 0))
                (integer :bookmarks (default 0))
                (integer :reviews (default 0))
                (integer :sold (default 0))
                (integer :views (default 0))
                (refer-to :currency)
                (refer-to :category)
                (check :price (>= :price 0))
                (check :quantity (>= :quantity 0))
                (check :reviews (>= :reviews 0))
                (check :views (>= :views 0))
                (check :sold (>= :sold 0))
                (text :description))))
  (down [] (drop (table :listing))))

(defmigration add-postage-table
  (up [] (create
           (tbl :postage
                (refer-to :user)
                (varchar :title 100)
                (float :price :not-null)
                (check :price (>= :price 0))
                (refer-to :currency))))
  (down [] (drop (table :postage))))

(defmigration add-orders-table
  (up [] (create
           (tbl :order
                (float :price)
                (check :price (>= :price 0))
                (float :postage_price)
                (check :postage_price (>= :postage_price 0))
                (float :refund_rate (default 0))
                (varchar :postage_title 100)
                (integer :postage_currency [:refer :currency :id])
                (integer :quantity)
                (check :quantity (>= :quantity 0))
                (boolean :hedged (default false))
                (float :hedge_fee)
                (boolean :reviewed (default false))
                (boolean :finalized (default false))
                (varchar :title 100)
                (timestamp :auto_finalize)
                (text :address)
                (check :address (< (length :address) 2000))
                (integer :seller_id [:refer :user :id :on-delete :set-null])
                (refer-to :currency)
                (refer-to :listing)
                (refer-to :postage)
                (refer-to :user)
                (smallint :status))))
  (down [] (drop (table :order))))

(defmigration add-orders-audit-table
  (up [] (create
          (tbl :orderaudit
               (refer-to :order)
               (refer-to :user)
               (smallint :status))))
  (down [] (drop (table :orderaudit))))

(defmigration add-escrow-table
  (up [] (create
           (tbl :escrow
                (integer :from [:refer :user :id :on-delete :set-null])
                (integer :to [:refer :user :id :on-delete :set-null])
                (float :amount)
                (float :btc_amount)
                (check :amount (>= :amount 0))
                (check :btc_amount (>= :btc_amount 0))
                (boolean :hedged (default false))
                (refer-to :currency)
                (refer-to :order)
                (varchar :status 10))))
  (down [] (drop (table :escrow))))

(defmigration add-reviews-table
  (up [] (create
           (tbl :review
                (boolean :published (default false))
                (refer-to :user)
                (integer :seller_id [:refer :user :id :on-delete :set-null])
                (refer-to :listing)
                (integer :order_id :unique :not-null)
                (text :content)
                (check :content (< (length :content) 2000))
                (integer :transaction)
                (smallint :rating :not-null (default 5))
                (check :rating (>= :rating 0))
                (boolean :shipped (default true)))))

  (down [] (drop (table :review))))

(defmigration add-audits-table
  (up [] (create
           (tbl :audit
                (refer-to :user)
                (varchar :role 10)
                (varchar :tx 64)
                (float :amount))))
  (down [] (drop (table :audit))))

(defmigration add-fee-table
  (up [] (create
          (tbl :fee
               (refer-to :order)
               (varchar :role 10)
               (float :amount))))
  (down [] (drop (table :fee))))

(defmigration add-resolutions-table
  (up [] (create
           (tbl :resolution
                (integer :from [:refer :user :id :on-delete :set-null])
                (refer-to :user)
                (integer :seller_id [:refer :user :id :on-delete :set-null])
                (refer-to :order)
                (boolean :applied)
                (boolean :user_accepted (default false))
                (boolean :seller_accepted (default false))
                (varchar :action 10);; extension or refund
                (integer :value (default 0))
                (text :content)
                (check :content (< (length :content) 2000))
                (check :value (>= :value 0)))))
  (down [] (drop (table :resolution))))

(defmigration add-mod-resolutions-table
  (up [] (create
          (tbl :modresolution
               (refer-to :user)
               (integer :buyer_id [:refer :user :id :on-delete :set-null])
               (integer :seller_id [:refer :user :id :on-delete :set-null])
               (refer-to :order)
               (integer :votes (default 0))
               (boolean :applied (default false))
               (integer :percent)
               (check :percent (>= :percent 0))
               (text :content)
               (check :content (< (length :content) 2000))
               )))
  (down [] (drop (table :modresolution))))

(defmigration add-mod-votes-table
  (up [] (create
          (tbl :modvote
               (index :vote_unique_constraint [:modresolution_id :user_id] :unique)
               (refer-to :modresolution)
               (refer-to :user))))
  (down [] (drop (table :modvote))))

(defmigration add-mod-comment-table
  (up [] (create
          (tbl :modcomment
               (refer-to :order)
               (refer-to :user)
               (text :content)
               (check :content (< (length :content) 2000))
               )))
  (down [] (drop (table :modcomment))))

(defmigration add-bookmarks-table
  (up [] (create
           (tbl :bookmark
                (index :bookmark_unique_constraint [:user_id :listing_id] :unique)
                (integer :user_id [:refer :user :id :on-delete :cascade])
                (integer :listing_id [:refer :listing :id :on-delete :cascade]))))
  (down [] (drop (table :bookmark))))

(defmigration add-fans-table
  (up [] (create
           (tbl :fan
                (index :fan_unique_constraint [:user_id :leader_id] :unique)
                (integer :user_id [:refer :user :id :on-delete :cascade])
                (integer :leader_id [:refer :user :id :on-delete :cascade]))))
  (down [] (drop (table :fan))))

(defmigration add-reports-table
  (up [] (create
           (tbl :report
                (index :report_unique_constraint [:object_id :user_id :type] :unique)
                (integer :object_id)
                (refer-to :user)
                (varchar :type 10 :not-null))))
  (down [] (drop (table :report))))

(defmigration add-posts-table
  (up [] (create
          (tbl :post
               (text :content)
               (check :content (< (length :content) 2000))
               (varchar :subject 100)
               (boolean :newsletter (default true))
               (boolean :published (default false))
               (boolean :public (default true))
               (refer-to :user))))
  (down [] (drop (table :post))))

(defmigration add-rating-to-listings
  (up [] (alter :add
                (table :listing
                       (float :rating (default 5.0)))))
  (down [] (alter :drop
                  (table :listing
                         (column :rating)))))

(defmigration add-shipping-table
  (up [] (create
          (table :ships_to
               (refer-to :listing)
               (refer-to :user)
               (refer-to :region))))
  (down [] (drop (table :ships_to))))

(defmigration add-order-forms-table
  (up [] (create
          (table :orderform
                 (text :content)
                 (check :content (< (length :content) 2000))
                 (refer-to :user))))
  (down [] (drop (table :orderform))))
