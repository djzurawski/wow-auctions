(ns wow-auctions.server.model
  (:require
   [cheshire.core :as cheshire]
   [environ.core :refer [env]]
   [incanter.stats :as stats]
   [clojure.java.jdbc :as jdbc]
   [org.httpkit.client :as http]
   [wow-auctions.server.db :as db]))



(def CLIENT-ID (env :client-id))
(def CLIENT-SECRET (env :client-secret))
(defonce TOKEN* (atom nil))
(defonce current-auction-data* (atom {}))


(defn refresh-token!
  []
  (let [token (-> @(http/post "https://us.battle.net/oauth/token"
                              {:basic-auth [CLIENT-ID CLIENT-SECRET]
                               :form-params {"grant_type" "client_credentials"}})
                  :body
                  (cheshire/parse-string true)
                  :access_token)]
    (reset! TOKEN* token)))


(defn api-get
  [url & opts]
  (let [{:keys [status body] :as resp} @(http/get url
                                        (merge {:query-params {:locale "en_US"
                                                               :access_token @TOKEN*}}
                                               opts))]
    (if (not= status 200)
      (do (refresh-token!)
          (recur url opts))
      (cheshire/parse-string body true))))


(defn realm-data-exists?
  [realm]
  (contains? @current-auction-data* realm))


(defn stale-data?
  [realm last-modified]
  (if (get @current-auction-data* realm)
    (> last-modified (get-in @current-auction-data* [realm :last-modified]))
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
  (let [body (api-get (str "https://us.api.blizzard.com/wow/auction/data/" realm))
        {:keys [lastModified url]} (first (:files body))]
    (when (fetch-data? realm lastModified)
      (let [auctions (:auctions (api-get url))]
        (swap! current-auction-data* assoc realm {:last-modified lastModified
                                                  :auctions (process-raw-auctions auctions)})))))


(defn get-realm-auctions
  [realm]
  (let [body (api-get (str "https://us.api.blizzard.com/wow/auction/data/" realm))
        {:keys [lastModified url]} (first (:files body))]
    {:auctions (:auctions (api-get url))
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


(defn get-item-history
  [item-id realm days]
  (jdbc/query
   (db/connection)
   (format "select * from auctions where timestamp > NOW() - interval '%s days' AND realm='%s' AND item_id=%s" days realm item-id)))
