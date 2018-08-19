(ns wow-auctions.client.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]

            [think.semantic-ui :as ui])
  (:import [goog.async Debouncer]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload
(defonce app-state* (atom {:text "Hello world!"
                           :realms []
                           :realm "malganis"
                           :items []
                           :item-id 15120
                           :item-name "anchor weed"}))

(defn plotly-chart
  "Plotly JS chart
  traces: vector of maps of all plots to display on chart in form:
          {:x [x-values]
           :y [y-values]
           :name 'name for data'
           :type 'histogram/bar/etc'}
  div-id:  chart div id seems to need to be unique to have mutlple charts on same page"
  [{:keys [width height title xlabel ylabel div-id]
    :or {width 800 height 200 div-id "chart"}}
   traces]
  (reagent/create-class
   {:component-did-mount
    (fn [this]
      (let [layout (clj->js {:title title
                             :xaxis {:title xlabel}
                             :yaxis {:title ylabel}
                             :width width
                             ;;:height height
                             })]
        (.newPlot js/Plotly div-id (clj->js traces) layout)))

    :component-did-update
    (fn [this]
      (let [[_ args traces] (reagent/argv this)
            layout (clj->js {:title title
                             :xaxis {:title xlabel}
                             :yaxis {:title ylabel}
                             :width width
                             ;;:height height
                             })]
        (.newPlot js/Plotly div-id (clj->js traces) layout)))

    :reagent-render
    (fn [arg-map]
      [:div {:id div-id}])}))

(defn debounce [f interval]
  (let [dbnc (Debouncer. f interval)]
    ;; We use apply here to support functions of various arities
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))


(defn search-fn
  [results* options event data]
  (let [term (:value (js->clj data :keywordize-keys true))
        pattern (re-pattern term)]
    (when (> (count term) 2)
      (reset! results*  (->> options
                             (filter (fn [{:keys [title]}]
                                       (re-find pattern title))))))))

(defn update-data!
  []
  (let [{:keys [item-id realm]} @app-state*]
    (swap! app-state* assoc :loading? true)
    (go (let [response (<! (http/get (str "/api/get-item/" realm "/"  item-id)))]
          (swap! app-state* assoc :data (:body response))
          (swap! app-state* assoc :loading? false)))))


(defn realm-search
  [realms]
  (let [results* (atom [])]
    (fn [realms]
      (let [options (->> realms (map (fn [realm]
                                         {:title realm})))
            debounced-search-fn (debounce (partial search-fn results* options) 250)]
        [ui/search
         {:min-characters 3
          :placeholder "Search Realm"
          :results @results*
          :on-search-change debounced-search-fn
          :on-result-select (fn [event data]
                              (let [realm (-> (js->clj data :keywordize-keys true)
                                              (:result)
                                              (:title))]
                                (swap! app-state* assoc :realm realm)
                                (update-data!)))}]))))

(defn item-search
  [items]
  (let [results* (atom [])]
    (fn [items]
      (let [options (->> items (map (fn [[item-name item-id]]
                                      {:title (name item-name)
                                        :description item-id})))
            debounced-search-fn (debounce (partial search-fn results* options) 250)]
        [ui/search
         {:min-characters 3
          :placeholder "Search Item"
          :results @results*
          :on-search-change debounced-search-fn
          :on-result-select (fn [event data]
                              (let [result (-> (js->clj data :keywordize-keys true)
                                                (:result)
                                                )
                                    item-id (:description result)
                                    item-name (:title result)]
                                (swap! app-state* assoc :item-id item-id :item-name item-name)
                                (update-data!)))}]))))

(defn stats-table
  [data]
  [ui/table]
  (let [sorted-data (sort data)
        cheapest (first sorted-data)
        second-cheapest (second sorted-data)
        total-auctions (count data)]
    [ui/table
     {:celled true :style {:width "300px" :margin-top "20px"}}
     [ui/table-body
      [ui/table-row
       [ui/table-cell [:b "Auction Count"]]
       [ui/table-cell total-auctions]]
      [ui/table-row
       [ui/table-cell [:b "Cheapest Per Item"]]
       [ui/table-cell cheapest]]
      [ui/table-row
       [ui/table-cell [:b "Second Cheapest Per Item"]]
       [ui/table-cell second-cheapest]]]]))




(defn app
  []
  (update-data!)
  (go
    (let [response (<! (http/get "/realms_us.json"))]
        (swap! app-state* assoc :realms (:body response))))
  (go (let [response (<! (http/get "/items.json"))]
        (swap! app-state* assoc :items (:body response))))
  (fn []
    (let [{:keys [item-name items realm realms data loading?]} @app-state*]
      [:div {:style {:width "50%" :padding-top "10px" :padding-left "30px"}}
       [:h1 (str item-name " on " realm)]

       [ui/grid {:width 2}
        [ui/grid-row [item-search (:items @app-state*)]
         [realm-search (:realms @app-state*)]]]


       (when (and data (not loading?))
         [:div
          [stats-table data]
          [ui/grid {:columns 2}
           [ui/grid-row
            [ui/grid-column
             (let [min-buyout (apply min data)
                   max-buyout (apply max data)]
               [plotly-chart {:div-id "histogram"
                              :title item-name
                              :width 500
                              :xlabel "Gold Per Item"
                              :ylabel "Count"
                              }
                [{:x data
                  :type :histogram
                  :xbins {:start min-buyout
                          :end max-buyout
                          :size (/ (- max-buyout min-buyout) 50)}}]])]
            [ui/grid-column
             [plotly-chart {:ylabel "Gold Per Item"
                            :width 400
                            :div-id "box"}
              [{:y data
                :name item-name
                :type :box
                :boxpoints :all
                :jitter 0.3
                :pointpos -1.8}]]]]]])])))


(reagent/render-component [app]
                          (. js/document (getElementById "app")))

(defn on-js-reload []
 ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
