(ns wow-auctions.server.server
  (:gen-class)
  (:require [bidi.ring :as bdr]
            [cheshire.core :as cheshire]
            [environ.core :refer [env]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [org.httpkit.client :as http]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [content-type-response wrap-content-type]]
            [wow-auctions.server.api :as api]
            [wow-auctions.routes :refer [app-routes]]))


(defn app
  [req]
  {:status 200
   :body
   (html
    [:head
     [:meta {:charset "UTF-8"}]]
    [:html
     [:body
      [:div#app]
      (include-js "/js/compiled/wow_auctions.js"
                  "https://cdn.plot.ly/plotly-latest.min.js")
      (include-css "https://cdn.jsdelivr.net/semantic-ui/2.2.10/semantic.min.css")
      ]])})


(defn route-dispatcher
  [route-key]
  (case route-key
      :get-item (fn [req] (api/get-item (:route-params req)))
      app))

(defn wrap-json-type
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (clojure.string/ends-with? (:uri request) "json")
        (assoc-in response [:headers "Content-Type"] "application/json")
        response))))

(defn make-bidi-routes
  []
  (-> (bdr/make-handler app-routes route-dispatcher)
      (wrap-resource "public")
      (wrap-json-type)
      (wrap-gzip)))


(defonce server* (atom nil))

(defn stop-server []
  (when-not (nil? @server*)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server* :timeout 100)
    (reset! server* nil)))

(defn -main [& [port]]
  (let [port (Integer.  (or port (env :port) 5000))]
    (reset! server* (run-server (make-bidi-routes) {:port port}))))
