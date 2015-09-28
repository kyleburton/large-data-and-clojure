(ns data-talk.data
  (:require
   [clojure.java.io               :as io]
   [clj-etl-utils.landmark-parser :as lp]
   [schema.core                   :as s]))

;; utils for preparing the data


(s/defn extract-emails-from-html [html-fname :- s/Str]
  (let [p       (lp/make-parser (slurp html-fname))
        segment (lp/extract
                 p
                 [[:forward-past "List emails address"]
                  [:forward-past ">"]
                  [:forward-past ">"]
                  [:forward-past ">"]]
                 [[:forward-to "</p>"]])]
    (.split segment "<br />")))

(s/defn extract-emails [output-file :- s/Str fnames :- [s/Str]]
  (when (.exists (io/file output-file))
    (.delete (io/file output-file)))
  (with-open [wtr (io/writer output-file)]
    (doseq [fname fnames]
      (doseq [email (extract-emails-from-html fname)]
        (.write wtr email)
        (.write wtr "\n")))))

(comment
  (extract-emails
   "data/emails.txt" 
   (->>
    (io/file "data")
    file-seq 
    (filter #(.endsWith (str %) ".html"))
    (map str)))

  (doseq [ii (range 1 6)]
    (extract-emails
     (format "data/%d-emails.txt" ii)
     [(format "data/%d.html" ii)]))

  )
