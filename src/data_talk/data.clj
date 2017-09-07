;; utils for preparing the example data
(ns data-talk.data
  (:require
   [com.github.kyleburton.clj-lfsr.core :as lfsr]
   [com.github.kyleburton.clj-lfsr.taps :as lfsr-taps]
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

(defonce all-npa-nxx (->> (.split (slurp "data/allutlzd.txt") "\n")
                          (map #(vec (map (fn [s] (.trim s))
                                          (.split % "\\t"))))
                          (drop 1)
                          (reduce (fn [acc [state npa-nxx ocn company ratecenter effectivedate use-field assigndate initial-growth pooled-code-file-updated]]
                                    (conj acc npa-nxx))
                                  #{})
                          shuffle
                          seq))

(defn make-random-phone-seq []
  (let [npa-nxx        (atom all-npa-nxx)
        next-npa-nxx   (fn []
                         (let [next-npa-nxx (first @npa-nxx)]
                           (swap! npa-nxx rest)
                           next-npa-nxx))
        nums           (atom [])
        curr-npa-nxx   (atom nil)
        next-phone-num (fn []
                         (when (empty? @nums)
                           (reset! curr-npa-nxx (next-npa-nxx))
                           (reset! nums (shuffle (range 10000))))
                         (let [next-num (first @nums)]
                           (swap! nums rest)
                           (format "%s-%04d" @curr-npa-nxx next-num)))]
    (repeatedly next-phone-num)))

(comment
  (take 10 (make-random-phone-seq))
  )

(defn generate-big-phones-file [ofname num-phones rand-prob]
  (let [phone-seq   (make-random-phone-seq)]
    (with-open [wtr (io/writer ofname)]
      ;; TODO: loop or dotimes
      (loop [[phone & phones] phone-seq
             num-phones       num-phones]
        (cond
          (or
           (= 0 num-phones)
           (not phone))
          :done

          :otherwise
          (do
            (.write wtr phone)
            (.write wtr "\n")
            (when (> rand-prob (rand))
              ;; (log/infof "DUPE: %s" phone)
              (.write wtr phone)
              (.write wtr "\n"))
            (recur phones (dec num-phones))))))))


(comment

  (do
    (time
     (generate-big-phones-file "some-phones.txt" 10000000 0.00001))
    :done)
  (> 0.1 (rand))

  (doseq [ii (range 100)]
    (let [rval (rand)]
     (log/infof "ii=%d rand=%f (< 0.1 %f) => %s"
                ii rval rval (< 0.1 rval))))


  )

(defn -main [& args]
  (let [[cmd & cmd-args] args]
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
      
      (= "generate-phones-file" cmd)
      (generate-big-phones-file "data/phones.1m.txt" 10000000 0.00001)
      
      :otherwise
      (.println System/out (format "main: generate-email-address-file|extract-emails|generate-phones-file")))))

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

