(ns wow-auctions.server.api
  (:require [wow-auctions.server.model :as model]
            [cheshire.core :as cheshire]))


(defn get-item
  [{:keys [realm item] :as route-params}]
  (model/update-realm-data! realm)
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (cheshire/generate-string (get-in @model/auction-data* [realm :auctions (java.lang.Integer/parseInt item)]))})
