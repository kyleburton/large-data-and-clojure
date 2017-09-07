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
                 [org.clojure/clojure                    "1.9.0-alpha17"]
                 [org.clojure/tools.logging              "0.3.1"]
                 [ch.qos.logback/logback-classic         "1.0.13"]
                 [org.clojure/tools.nrepl                "0.2.12"]
                 [cider/cider-nrepl                      "0.13.0"]
                 [org.clojure/data.json                  "0.2.6"]
                 [clj-time                               "0.14.0"]
                 [http-kit                               "2.2.0"]
                 [prismatic/schema                       "1.1.6"]

                 [com.github.kyleburton/clj-bloom        "1.0.5"]
                 [com.github.kyleburton/clj-etl-utils    "1.0.95"]

                 [corpus-enormous                        "0.1.5"]
                 [com.github.kyleburton/clj-lfsr         "1.3.4"]
                 ])

