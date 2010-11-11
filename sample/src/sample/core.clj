(ns sample.core
  (:import [java.io File])
  (:use [clj-etl-utils.lang-utils :only (raise)])
  (:require [clj-etl-utils.io                    :as io]
            [clj-etl-utils.sequences             :as sequencese]
            [com.github.kyleburton.clj-lfsr.core :as lfsr]
            [com.github.kyleburton.clj-bloom     :as bloom]
            [clojure.contrib.pprint              :as pp]
            [clojure.contrib.duck-streams        :as ds]))

(defn left-pad [s chr len]
  (let [s (str s)]
    (if (< (count s) len)
      (str (apply str (repeat (- len (count s)) chr))
           s)
      s)))


(def gen-random-phone-number
  (let [rnd (java.util.Random.)
        npas       (vec (map (fn [val] (left-pad (+ 100 val) "0" 3)) (range 900)))
        npas-count (count npas)
        nxxs       (vec (map (fn [val] (left-pad val "0" 3)) (range 1000)))
        nxxs-count (count nxxs)
        nums       (vec (map (fn [val] (left-pad val "0" 4)) (range 10000)))
        nums-count (count nums)]
    (fn gen-random-phone-number []
      (format "(%s) %s-%s"
              (npas (.nextInt rnd npas-count))
              (nxxs (.nextInt rnd nxxs-count))
              (nums (.nextInt rnd nums-count))))))

(defn random-phone-number-seq []
  (lazy-cat
   (map (fn [x] (gen-random-phone-number)) (range 10))
   (random-phone-number-seq)))

;; (take 10 (random-phone-number-seq))

(defn gen-sample-phone-file [file-name & [max-count]]
  (with-open [outp (java.io.PrintWriter. file-name)]
    (doseq [n (take (or max-count 100) (random-phone-number-seq))]
      (.println outp n))))

(comment

  (time
   (gen-sample-phone-file "phone-nums.txt" 10000))
  ;; 86s

  (time
   (gen-sample-phone-file "phone-nums.txt" 2000000))
  ;; 9659s

  )


(defn add-identifier [in-file out-file id-seq]
  (if-not (.exists (File. out-file))
    (with-open [outp (ds/writer out-file)]
      (doseq [[id l] (map (fn [id l] [id l])
                          id-seq
                          (ds/read-lines in-file))]
        (.write outp (str id))
        (.write outp "\t")
        (.write outp l)
        (.write outp "\n")))))


(comment

  (add-identifier
   "phone-nums.txt"
   "phone-nums-with-ids.txt"
   (iterate inc 1))

  (add-identifier
   "phone-nums.txt"
   "phone-nums-with-lfsr-ids.txt"
   (drop (.nextInt (java.util.Random.) 9999) (map :state (lfsr/lfsr-lazy-seq (lfsr/lfsr 64 [64 63 61 60])))))

  (take 10 (map #(format "ID-%022X" %1)
                (drop (.nextInt (java.util.Random.) 9999) (map :state (lfsr/lfsr-lazy-seq (lfsr/lfsr 64 [64 63 61 60]))))))


  )



(defn find-dupes-naieve [inp-seq]
  (reduce (fn [res item]
            (assoc res item (inc (get res item 0))))
          {}
          (map #(second (.split %1 "\t")) inp-seq)))

(defn evict-singletons [m]
  (loop [dps {}
         [k & ks] (keys m)]
    (cond
      (not k)  dps
      (> (get m k) 1)  (recur (assoc dps k (get m k)) ks)
      :else          (recur dps ks))))



(comment

  (evict-singletons (find-dupes-naieve (take 100 (ds/read-lines "phone-nums-with-lfsr-ids-some-dupes.txt"))))

  ;; With the lieningen default settings, This blows up w/an OOM
  (find-dupes-naieve (ds/read-lines "phone-nums-with-lfsr-ids.txt"))

  )


(defn find-dupes-with-bloom-filter [inp-seq expected-size fp-prob]
  (let [flt (bloom/make-optimal-filter expected-size fp-prob)]
    (reduce (fn [res item]
              (if-not (bloom/include? flt item)
                (do
                  (bloom/add! flt item)
                  res)
                (assoc res item (inc (get res item 1)))))
            {}
            (map #(second (.split %1 "\t")) inp-seq))))

(comment

  (time
   (pp/pprint
    (find-dupes-with-bloom-filter (take 100 (ds/read-lines "phone-nums-with-lfsr-ids-some-dupes.txt"))
      2000000 0.01)))

  (time
   (pp/pprint
    (find-dupes-with-bloom-filter
      (take 200000 (ds/read-lines "phone-nums-with-lfsr-ids.txt"))
      2000000 0.01)))

  ;; 8730.217 for 100000

  ;; the full monty
  (time
   (pp/pprint
    (find-dupes-with-bloom-filter
      (ds/read-lines "phone-nums-with-lfsr-ids.txt")
      2000000 0.01)))

  ;; woot: 182004.084 msecs!

  )


(defn count-area-codes [inp-seq]
  (reduce (fn [m line]
            (let [phnum         (second (.split line "\t"))
                  [_ area-code] (first (re-seq #"\((\d+)\)" phnum))]
              (assoc m area-code (inc (get m area-code 0)))))
          {}
          inp-seq))

(comment

  (time
   (count-area-codes (take 100 (ds/read-lines "phone-nums-with-lfsr-ids.txt"))))

  (time
   (pp/pprint
    (count-area-codes (ds/read-lines "phone-nums-with-lfsr-ids.txt"))))

  ;; "Elapsed time: 40460.279 msecs"

)


;; make sure you split the files first...see split-file.sh:
;;    split -l 100000 phone-nums-with-lfsr-ids.txt working-dir/inp-

;;     (pmap some-expensive-operation
;;       (map str (filter #(.isFile %) (.listFiles (java.io.File. "working-directory/")))))

(comment

  ;; this gets you a sequence of maps that will then need to be merged...
  (time
   (pp/pprint
    (map (fn [inp-file]
           (count-area-codes (ds/read-lines inp-file)))
         (take 2 (map str (filter #(.isFile %) (.listFiles (java.io.File. "working-dir/"))))))))

  (apply
   merge-with +
   (map (fn [inp-file]
          (count-area-codes (ds/read-lines inp-file)))
        (map str (filter #(.isFile %) (.listFiles (java.io.File. "working-dir/"))))))

  ;; 7244.75s for 200k

  (time
   (pp/pprint
    (reduce
     (fn [res counts]
       (merge-with + res counts))
     (map (fn [inp-file]
            (count-area-codes (ds/read-lines inp-file)))
          (map str (filter #(.isFile %) (.listFiles (java.io.File. "working-dir/"))))))))

  ;; "Elapsed time: 43171.102 msecs"

  (time
   (pp/pprint
    (reduce
     (fn [res counts]
       (merge-with + res counts))
     (pmap (fn [inp-file]
             (count-area-codes (ds/read-lines inp-file)))
          (map str (filter #(.isFile %) (.listFiles (java.io.File. "working-dir/"))))))))

  ;; "Elapsed time: 34740.162 msecs"

  ;; a bit faster on my laptop (Mac w/Core 2 DUO), performance on your
  ;; system will vary based on your system's CPUs and IO

  ;; also, the hidden cost here is the time it took to the split
  ;; (which is also 1x IO) which, on my macbook makes this solution
  ;; less optimal than just counting the entire file...

  )

(comment

  (time
   (pp/pprint
    (let [inp-file "phone-nums-with-lfsr-ids.txt"]
      (reduce
       (fn [res counts]
         (merge-with + res counts))
       (pmap (fn [[start end]]
               (count-area-codes (clj-etl-utils.io/read-lines-from-file-segment inp-file start end)))
             (partition 2 1 (clj-etl-utils.io/byte-partitions-at-line-boundaries inp-file (* 1024 1024))))))))

  "Elapsed time: 31985.664 msecs"

  (apply + (map (fn [[s e]]
                  (- e s)) (partition 2 1 (clj-etl-utils.io/byte-partitions-at-line-boundaries "phone-nums-with-lfsr-ids.txt" (* 1024 1024)))))

  (second (partition 2 1 (clj-etl-utils.io/byte-partitions-at-line-boundaries "phone-nums-with-lfsr-ids.txt" (* 1024 1024))))
  (1048577 2097175)

  (time
   (count (clj-etl-utils.io/read-lines-from-file-segment "phone-nums-with-lfsr-ids.txt" 0 3532265)))


  (sequences/random-sample-seq (ds/read-lines "phone-nums.txt")
                               2000000
                               200)


  (time
   (count (ds/read-lines "working-dir/inp-aa")))

  )

