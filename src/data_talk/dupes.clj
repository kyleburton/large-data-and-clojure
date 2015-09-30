(ns data-talk.dupes
  (:require
   [com.github.kyleburton.clj-bloom :as bloom]
   [schema.core                     :as s]
   [clojure.java.io                 :as io]
   [clojure.tools.logging           :as log]))

;; 1. Likely Duplicates (remember, Bloom Filters can have FPs) in the one pass
;; 2. The actual Duplicates in two passes.

;; NB: you create data/emails.txt by running
;;    'bake extract-emails'
;; in the project root
(defonce emails-file "data/emails.txt")

(def num-lines (memoize (fn [fname]
                          (with-open [rdr (io/reader fname)]
                            (count (line-seq rdr))))))
(defn find-likely-dupes-in-single-pass [inp-seq psize fp-prob]
  (let [flt (bloom/make-optimal-filter psize fp-prob)]
    (reduce
     (fn [likely-dupes item]
       ;; not in the filter? not a dupe (no FPs)
       (if (bloom/include? flt item)
         (do
           ;; (log/infof "dupe: %s" item)
           (conj likely-dupes item))
         (do
           ;; (log/infof "add:  %s" item)
           (bloom/add! flt item)
           likely-dupes)))
     #{}
     inp-seq)))

(comment

  (let [data-file   "data/fruit.txt"
        output-file "fruit-dupes.txt"]
    (with-open [rdr (io/reader data-file)
                wtr (io/writer output-file)]
      (doseq [item (find-likely-dupes-in-single-pass
                    (line-seq rdr)
                    (num-lines data-file)
                    0.05)]
        (.write wtr item)
        (.write wtr "\n"))))


  (let [data-file emails-file
        output-file "dupe-emails.txt"]
    (with-open [rdr (io/reader data-file)
                wtr (io/writer output-file)]
      (doseq [item (find-likely-dupes-in-single-pass
                    (line-seq rdr)
                    (num-lines data-file)
                    0.05)]
        (.write wtr item)
        (.write wtr "\n"))))


  (let [data-file   "data/random-emails.txt"
        output-file "likely-dupe-emails.txt"]
    (with-open [rdr (io/reader data-file)
                wtr (io/writer output-file)]
      (doseq [item (find-likely-dupes-in-single-pass
                    (line-seq rdr)
                    (num-lines data-file)
                    0.001)]
        (.write wtr item)
        (.write wtr "\n"))))



  )


;; Finding the literal dupes requires 2 passes:
;;  p.1: populate the bloom filter
;;  p.2: record the counts of any items that are in the filter
;;       where the count=1 it's a FP
;;       where the count>1 it's a dupe

(defn build-bloom-filter [inp-seq psize fp-prob]
  (let [flt (bloom/make-optimal-filter psize fp-prob)]
    (doseq [item inp-seq]
      (bloom/add! flt item))
    flt))

(defn find-dupes [flt inp-seq]
  (let [item-counts (reduce
                     (fn [acc item]
                       (if-not (bloom/include? flt item)
                         acc
                         (assoc acc item (inc (get acc item 0)))))
                     {}
                     inp-seq)]
    (->>
     item-counts
     (filter (fn [[item count]] (< 1 count)))
     (map first))))

(comment

  (let [input-file  "data/fruit.txt"
        flt         (with-open [rdr (io/reader input-file)]
                      (build-bloom-filter
                       (line-seq rdr)
                       (num-lines input-file)
                       0.01))]
    (with-open [rdr (io/reader input-file)]
      (find-dupes flt (line-seq rdr))))

  ;; => ("ugli fruit")


  (let [input-file  "data/random-emails.txt"
        output-file "actual-dupe-emails.txt"
        flt         (with-open [rdr (io/reader input-file)]
                      (build-bloom-filter
                       (line-seq rdr)
                       (num-lines input-file)
                       0.001))]
    (with-open [rdr (io/reader input-file)
                wtr (io/writer output-file)]
      (doseq [item (find-dupes flt (line-seq rdr))]
        (.write wtr item)
        (.write wtr "\n"))))

  )
