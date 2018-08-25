(ns wow-auctions.server.scraper
  (:require [cheshire.core :as cheshire]
            [camel-snake-kebab.core :refer [->snake_case]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.java.jdbc :as jdbc]
            [environ.core :refer [env]]
            [org.httpkit.client :as http]
            [wow-auctions.server.db :as db]
            [wow-auctions.server.model :as model]))

(def API-KEY (env :wow-api-key))

(defn auction-prices->db-row
  [auctions timestamp realm]
  (->> auctions
       (map (fn [[item-id prices]]
              (-> (model/prices->stats prices)
                  (assoc :item-id item-id :count (count prices) :timestamp (java.sql.Timestamp. timestamp) :realm realm))))))


(defn insert-auctions!
  [realm]
  (let [{:keys [timestamp auctions]} (model/get-realm-auctions realm)
        row-order [:timestamp :item-id :count :min :first-quartile :median :third-quartile :max :realm]
        rows  (-> (model/auctions->item-prices auctions)
                  (auction-prices->db-row timestamp realm))
        txs (->> rows
                 (mapv (fn [{:keys [:timestamp :item-id :count :min :first-quartile :median :third-quartile :max :realm]}]
                        [timestamp item-id count min first-quartile median third-quartile max realm])))]
    (jdbc/insert-multi! (db/connection)
                          "auctions"
                          (mapv ->snake_case row-order) txs)))

(defn -main
  [& args]
  (doseq [realm (cheshire/parse-string (slurp (clojure.java.io/resource "public/realms_us.json")) true)]
    (insert-auctions! (:slug realm))))
