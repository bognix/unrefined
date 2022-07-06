(ns fpsd.unrefined.server
  (:require [mount.core :as mount]
            [fpsd.configuration :refer [config]]
            [fpsd.routes :refer [http-server]]
            [fpsd.nrepl :refer [nrepl-server]])
  (:gen-class))

(defn -main [& _args]
  (println "Starting unrefined service...")
  (println (mount/start))
  (println "Ready to accept http connections at " (-> config :http :port))
  (println "Ready to accept nrepl connections at " (-> config :nrepl :port))

  (loop []
    (Thread/sleep 1000)
    (recur))
  (println "Stopping unrefined service")
  (mount/stop))
