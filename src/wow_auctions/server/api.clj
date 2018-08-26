(ns wow-auctions.server.api
  (:require [wow-auctions.server.model :as model]
            [cheshire.core :as cheshire]))


(defn get-item-current
  [{:keys [realm item] :as route-params}]
  (model/update-current-data! realm)
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (cheshire/generate-string (get-in @model/current-auction-data* [realm :auctions (java.lang.Integer/parseInt item)]))})


(defn get-item-history
  [{:keys [realm item] :as route-params}]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (cheshire/generate-string (model/get-item-history item realm 30))})
