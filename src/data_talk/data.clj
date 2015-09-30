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

;; From: http://stackoverflow.com/questions/15996035/clojure-way-of-reading-large-files-and-transforming-data-therein
(defn lazy-file-lines
  "open a (probably large) file and make it a available as a lazy seq of lines"
  [filename]
  (letfn [(helper [rdr]
            (lazy-seq
             (if-let [line (.readLine rdr)]
               (cons line (helper rdr))
               (do (.close rdr) nil))))]
    (helper (clojure.java.io/reader filename))))

(defn lnames-seq []
  (->>
    "data/dist.all.last"
    lazy-file-lines
    (map #(-> % (.split " ") first))))

(defn fnames-seq []
  (concat
   (->>
    "data/dist.female.first"
    lazy-file-lines
    (map #(-> % (.split " ") first)))
   (->>
    "data/dist.male.first"
    lazy-file-lines
    (map #(-> % (.split " ") first)))))

(def lnames (vec (lnames-seq)))
(def fnames (vec (fnames-seq)))

(defn rand-lname []
  (rand-nth lnames))

(defn rand-fname []
  (rand-nth fnames))

(def email-domains (vec (lazy-file-lines "data/email-domains.txt")))
(defn rand-domain []
  (rand-nth email-domains))

(defn rand-email []
  (let [fname (.toLowerCase (rand-fname))
        lname (.toLowerCase (rand-lname))
        domain (rand-domain)]
    (str fname "." lname "@" domain)))

(defn generate-email-address-file []
  (with-open [wtr (io/writer "data/random-emails.txt")]
    (.write wtr "i.ma@dupe\n")
    (doseq [email (->>
                   (repeatedly rand-email)
                   (take 100000))]
      (.write wtr email)
      (.write wtr "\n"))
    (.write wtr "i.ma@dupe\n")))

(defn -main [& args]
  (let [cmd (or (first args) "extract-emails")]
    (cond
      (= "generate-email-address-file" cmd)
      (generate-email-address-file)
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

