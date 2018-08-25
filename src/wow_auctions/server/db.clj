(ns wow-auctions.server.db
  (:require [clojure.java.jdbc :as jdbc]
            [environ.core :refer [env]]))

(defn connection
  []
  {:dbtype "postgresql",
   :dbname "auctions",
   :host "wow-auctions.com"
   :user "postgres"
   :password (env :pgpassword)})
