(ns wow-auctions.routes)

(def app-routes ["/" {"" :app
                      "api/get-item/current/" {[:realm "/" :item ] :get-item-current}
                      "api/get-item/history/" {[:realm "/" :item ] :get-item-history}}])
