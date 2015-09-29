(defproject clojure-and-big-data "0.1.0-SNAPSHOT"
  :description "Code to support the talk"
  :url "https://github.com/kyleburton/large-data-and-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main ^:skip-aot data-talk.core
  :jvm-opts ["-Xmx512m" "-server"]
  :profiles 
  {:dev 
   {:resource-paths["dev-resources"]
    :plugins       []}}
  :dependencies [
                 [org.clojure/tools.logging              "0.3.1"]
                 [ch.qos.logback/logback-classic         "1.0.13"]
                 [org.clojure/clojure                    "1.7.0"]
                 [org.clojure/tools.nrepl                "0.2.11"]
                 ;; NB: cider-nrepl is up to 0.9.1
                 [cider/cider-nrepl                      "0.7.0"]
                 [prismatic/schema                       "1.0.1"]
                 [cheshire                               "5.5.0"]
                 [com.github.kyleburton/clj-xpath        "1.4.5"]
                 [com.github.kyleburton/clj-bloom        "1.0.4"]
                 [com.github.kyleburton/clj-etl-utils    "1.0.94"]])

