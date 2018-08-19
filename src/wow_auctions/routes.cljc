(ns wow-auctions.routes)

(def app-routes ["/" {"" :app
                 "api/get-item/" {[:realm "/" :item ] :get-item}}])
