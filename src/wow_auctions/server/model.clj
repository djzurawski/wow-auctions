(ns wow-auctions.server.model
  (:require [cheshire.core :as cheshire]
            [environ.core :refer [env]]
            [org.httpkit.client :as http]))


(defonce auction-data* (atom {"malganis" {:last-modified 345}}))

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
       #_(when (or (zero? buyout) (println "Zero")) )
       (if-not (zero? buyout)
         (update res item conj (float (/ (/ buyout copper-per-gold) quantity)))
         res))
     {} auctions)))


(defn update-realm-data!
  [realm]
  (let [status (parse-body @(http/get (str "https://us.api.battle.net/wow/auction/data/" realm)
                                      {:query-params {:locale "en_US"
                                                      :apikey API-KEY}}))
        {:keys [lastModified url]} (first (:files status))]
    (when (fetch-data? realm lastModified)
      (let [auctions (:auctions (parse-body @(http/get url)))]
        (println "fetching realm" realm)
        (swap! auction-data* assoc realm {:last-modified lastModified
                                          :auctions (process-raw-auctions auctions)})))))
