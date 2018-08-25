(ns wow-auctions.server.model
  (:require
   [cheshire.core :as cheshire]
   [environ.core :refer [env]]
   [incanter.stats :as stats]
   [clojure.java.jdbc :as jdbc]
   [org.httpkit.client :as http]))


(defonce auction-data* (atom {}))

(def API-KEY (env :wow-api-key))

(defn parse-body
  [response]
  (try (cheshire/parse-string (:body response) true)
       (catch Exception e
         {})))


(defn realm-data-exists?
  [realm]
  (contains? @auction-data* realm))


(defn stale-data?
  [realm last-modified]
  (if (get @auction-data* realm)
    (> last-modified (get-in @auction-data* [realm :last-modified]))
    true))


(defn fetch-data?
  [realm last-modified]
  (or (not (realm-data-exists? realm))
      (stale-data? realm last-modified)))


(defn zero-or-nil?
  [x]
  (or (zero? x) (nil? x)))


(defn process-raw-auctions
  "Converts auction data from Blizzard API to {item-number [buyout-price-per-unit]}"
  [auctions]
  (let [copper-per-gold 10000]
    (reduce
     (fn [res {:keys [item buyout quantity] :as auctions}]
       (if-not (zero? buyout)
         (update res item conj (float (/ (/ buyout copper-per-gold) quantity)))
         res))
     {} auctions)))


(defn update-current-data!
  "Updates in memory most recent snapshot"
  [realm]
  (let [status (parse-body @(http/get (str "https://us.api.battle.net/wow/auction/data/" realm)
                                      {:query-params {:locale "en_US"
                                                      :apikey API-KEY}}))
        {:keys [lastModified url]} (first (:files status))]
    (when (fetch-data? realm lastModified)
      (let [auctions (:auctions (parse-body @(http/get url)))]
        (swap! auction-data* assoc realm {:last-modified lastModified
                                          :auctions (process-raw-auctions auctions)})))))

(defn get-realm-auctions
  [realm]
  (let [status (parse-body @(http/get (str "https://us.api.battle.net/wow/auction/data/" realm)
                                      {:query-params {:locale "en_US"
                                                      :apikey API-KEY}}))
        {:keys [lastModified url]} (first (:files status))]
    {:auctions (:auctions (parse-body @(http/get url)))
     :timestamp lastModified}))


(defn auctions->item-prices
  "Converts auction data from Blizzard API to {item-number [buyout-price-per-unit]}"
  [auctions]
  (let [copper-per-gold 10000]
    (reduce
     (fn [res {:keys [item buyout quantity] :as auctions}]
       (if-not (zero? buyout)
         (update res item conj (float (/ (/ buyout copper-per-gold) quantity)))
         res))
     {} auctions)))


(defn prices->stats
  [prices]
  (->> (map (fn [label value]
              [label value])
            [:min :first-quartile :median :third-quartile :max] (stats/quantile prices))
       (into {})))
