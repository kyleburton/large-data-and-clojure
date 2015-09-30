;; utils for preparing the example data
(ns data-talk.data
  (:require
   [clojure.java.io               :as io]
   [clj-etl-utils.landmark-parser :as lp]
   [schema.core                   :as s]
   [clojure.tools.logging         :as log]))

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
  (log/infof "filling %s with email addresses from %s" output-file fnames)
  (when (.exists (io/file output-file))
    (.delete (io/file output-file)))
  (with-open [wtr (io/writer output-file)]
    (doseq [fname fnames]
      (doseq [email (extract-emails-from-html fname)]
        (.write wtr email)
        (.write wtr "\n"))))
  (log/infof "all done."))

(defn -main [& args]
  (let [cmd (or (first args) "extract-emails")]
    (cond
     (= "extract-emails" cmd)
     (extract-emails
      "data/emails.txt"
      (->>
       (io/file "data")
       file-seq 
       (filter #(.endsWith (str %) ".html"))
       (map str)))
     :otherwise
     (extract-emails
      "data/emails.txt"
      (->>
       (io/file "data")
       file-seq 
       (filter #(.endsWith (str %) ".html"))
       (map str))))))

(comment
  (extract-emails
   "data/emails.txt" 
   (->>
    (io/file "data")
    file-seq 
    (filter #(.endsWith (str %) ".html"))
    (map str))w)

  (doseq [ii (range 1 6)]
    (extract-emails
     (format "data/%d-emails.txt" ii)
     [(format "data/%d.html" ii)]))

  )
