(ns data-talk.talk
  (:require
   [com.github.kyleburton.clj-lfsr.core :as lfsr]
   [com.github.kyleburton.clj-lfsr.taps :as lfsr-taps]
   [clojure.java.io                     :as io]
   [clj-etl-utils.landmark-parser       :as lp]
   [schema.core                         :as s]
   [clojure.tools.logging               :as log]
   [clj-etl-utils.sequences             :as etl-seq]
   [clj-etl-utils.io                    :as etl-io]
   [com.github.kyleburton.clj-bloom     :as bf]))


(def input-files {:people "data/people.1m.txt"
                  :phones "data/phones.10m.txt"
                  :emails "data/random-emails.txt"})


(comment
  ;; random sample the phones file
  ;; data/phones.10m.txt 10000085 lines, 124Mb of data

  (time
   (with-open [rdr (-> input-files :phones io/reader)]
     (etl-seq/random-sample-seq (line-seq rdr) 10000085 10)))

  ;; haha, it's lazy

  (time
   (with-open [rdr (-> input-files :phones io/reader)]
     (vec (etl-seq/random-sample-seq (line-seq rdr) 10000085 10))))

  ;; ~5-10s
  
  (time
   (let [sampler (etl-seq/make-reservoir-sampler 10)]
     (with-open [rdr (-> input-files :phones io/reader)]
       (sampler (line-seq rdr)))))
  ;; ~1-2s

  )


(comment

  ;; Find dummy values using a bloom filter
  (bf/make-optimal-filter 10000000 0.1)
  (bf/make-optimal-filter 10000000 0.01)
  (bf/make-optimal-filter 10000000 0.001)
  
  (time
   (def likely-dupe-phones
     (let [flt (bf/make-optimal-filter 10000000 0.001)]
       (with-open [rdr (-> input-files :phones io/reader)]
         (reduce
          (fn [acc item]
            (if-not (bf/include? flt item)
              (do
                (bf/add! flt item)
                acc)
              (assoc acc
                     item
                     (inc (acc item 0)))))
          {}
          (line-seq rdr))))))

  (time
   (def acutal-counts-of-likely-dupe-phones
     (with-open [rdr (-> input-files :phones io/reader)]
       (reduce
        (fn [acc item]
          (if (contains? likely-dupe-phones item)
            (assoc acc
                   item
                   (inc (acc item 0)))
            acc))
        {}
        (line-seq rdr)))))

  (->>
   acutal-counts-of-likely-dupe-phones
   (filter
    #(> (second %) 1))
   count)

  (->>
   acutal-counts-of-likely-dupe-phones
   (filter
    #(> (second %) 1)))
  

  )


;; XXX-XXX-XXXX
(defn count-area-codes [inp-seq]
  (reduce
   (fn [acc line]
     (let [area-code (.substring line 0 3)]
       (assoc acc area-code (inc (acc area-code 0)))))
   {}
   inp-seq))

(comment

  ;; straight-forward linear, naive
  (time
   (do
     (with-open [rdr (-> input-files :phones io/reader)]
       (count-area-codes (line-seq rdr)))
     :done))
  ;; ~25s


  ;; let's chunk it up after the split
  (time
   (do
     (apply
      merge-with +
      (map (fn [ifile]
             (with-open [rdr (io/reader ifile)]
               (count-area-codes (line-seq rdr))))
           (map
            str
            (filter #(and (.startsWith (str %) "data/phones-parts") (.isFile %))
                    (.listFiles
                     (java.io.File. "data"))))))
     :done))
  ;; ~25s

  ;; cool lets change that into a pmap
  (time
   (do
     (apply
      merge-with +
      (pmap (fn [ifile]
              (with-open [rdr (io/reader ifile)]
                (count-area-codes (line-seq rdr))))
            (map
             str
             (filter #(and (.startsWith (str %) "data/phones-parts") (.isFile %))
                     (.listFiles
                      (java.io.File. "data"))))))
     :done))
  ;; ~8s
  ;; NB: also watch TOP or the Activity Monitor

  ;; lets just process it in parts
  (time
   (let [inp-file (-> input-files :phones)]
     (reduce
      (fn [res counts]
        (merge-with + res counts))
      (pmap (fn [[start end]]
              (count-area-codes
               (etl-io/read-lines-from-file-segment inp-file start end)))
            (partition 2 1 (etl-io/byte-partitions-at-line-boundaries inp-file (* 1024 1024)))))
     :done))
  ;; ~8s

  ;; let's take a quick look at where those line boundardies break down
  (->>
   (etl-io/byte-partitions-at-line-boundaries
    (-> input-files :phones)
    (* 1024 1024))
   (take 5)
   vec) 
  
  [0 1048580 2097160 3145740 4194320]

  ;; not exactly at 1mb boundaries, which would have been
  [0 1048576 2097152 3145728 4194304]

  (->>
   (etl-io/byte-partitions-at-line-boundaries
    (-> input-files :phones)
    (* 1024 1024))
   (take 5)
   (partition 2 1)
   vec)
  ;; read from .. to
  [(0 1048580) (1048580 2097160) (2097160 3145740) (3145740 4194320)]

  ;; check out the actual results of this ...
  (time
   (do
     (def results
       (reduce
        (fn [res counts]
          (merge-with + res counts))
        (pmap (fn [[start end]]
                (count-area-codes
                 (etl-io/read-lines-from-file-segment (-> input-files :phones) start end)))
              (partition 2 1 (etl-io/byte-partitions-at-line-boundaries (-> input-files :phones) (* 1024 1024))))))
     :done))


  )


