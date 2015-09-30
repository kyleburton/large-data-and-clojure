(ns data-talk.dupes
  (:require
   [com.github.kyleburton.clj-bloom :as bloom]
   [schema.core                     :as s]
   [clojure.java.io                 :as io]
   [clojure.tools.logging           :as log]))

;; 1. Likely Duplicates in the first pass
;; 2. The actual Duplicates in two passes.

;; NB: create this file by running 'bake extract-emails' in the
;; project root
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
           (log/infof "dupe: %s" item)
           (conj likely-dupes item))
         (do
           (log/infof "add:  %s" item)
           (bloom/add! flt item)
           likely-dupes)))
     #{}
     inp-seq)))

(comment

  (with-open [rdr (io/reader emails-file)
              wtr (io/writer "likely-dupes.txt")]
    (let [likely-dupes (find-likely-dupes-in-single-pass
                        (line-seq rdr)
                        (num-lines emails-file)
                        0.05)]
      (doseq [item likely-dupes]
        (.write wtr item)
        (.write wtr "\n"))))


  (let [fname "data/fruit.txt"]
   (with-open [rdr (io/reader fname)
               wtr (io/writer "fruit-dupes.txt")]
     (let [likely-dupes (find-likely-dupes-in-single-pass
                         (line-seq rdr)
                         (num-lines fname)
                         0.05)]
       (doseq [item likely-dupes]
         (.write wtr item)
         (.write wtr "\n")))))



  )


;; Finding all of the actual dupes requires 2 passes:
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

  (def flt
    (with-open [rdr (io/reader emails-file)]
      (build-bloom-filter
       (line-seq rdr)
       (num-lines emails-file)
       0.01)))

  (def dupes
    (with-open [rdr (io/reader emails-file)]
      (find-dupes flt (line-seq rdr))))

  (count dupes)
  
  (def dupes-1
    (with-open [rdr (io/reader "data/1-emails.txt")]
      (find-dupes flt (line-seq rdr))))

  (count dupes-1)
  (take 10 dupes-1)

  (def dupes-1-2
    (with-open [rdr  (io/reader "data/1-emails.txt")
                rdr2 (io/reader "data/2-emails.txt")]
      (find-dupes flt (concat (line-seq rdr)
                              (line-seq rdr2)))))

  (count dupes-1-2)
  (take 10 dupes-1-2)


  )
