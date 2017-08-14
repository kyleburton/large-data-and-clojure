(ns data-talk.core
  (:require
   [clojure.tools.nrepl.server :refer [start-server stop-server]]
   [cider.nrepl                :refer [cider-nrepl-handler]]
   [clojure.tools.logging      :as log]
   [schema.core                :as s]))

(defonce nrepl-server (atom nil))

(defonce config
  (atom
   {:nrepl {:port 4011}
    :apps  []}))

(defn -main
  "Start the service."
  [& args]
  (s/set-fn-validation! true)
  (reset! nrepl-server (start-server
                        :port (-> @config :nrepl :port)
                        :handler cider-nrepl-handler))
  (log/infof "nREPL server started: %s" (-> @config :nrepl :port)))


