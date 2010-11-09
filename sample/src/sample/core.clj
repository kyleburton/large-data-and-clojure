(ns sample.core
  (:import [java.io File])
  (:use [clj-etl-utils.lang-utils :only (raise)])
  (:require [clj-etl-utils.io                    :as io]
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
   (drop (.nextInt (java.util.Random. 9999)) (map :state (lfsr/lfsr-lazy-seq (lfsr/lfsr 64 [64 63 61 60])))))

  )


(defn find-dupes-naieve [inp-seq]
  (loop [[l & lines] inp-seq
         res {}]
    (if-not l
      (loop [dps {}
             [k & ks] (keys res)]
        (cond
          (not k)  dps
          (> (res k) 1)  (recur (assoc dps k (res k)) ks)
          :else          (recur dps ks)))
      (let [[id phone-number] (.split l "\t")]
        (recur lines
               (assoc res
                 phone-number
                 (inc (res phone-number 0))))))))

(comment

  (find-dupes-naieve (take 100 (ds/read-lines "phone-nums-with-lfsr-ids-some-dupes.txt")))
  (find-dupes-naieve (take 1000000 (ds/read-lines "phone-nums-with-lfsr-ids.txt")))

  )


(defn find-dupes-with-bloom-filter [inp-seq expected-size fp-prob]
  (let [flt (bloom/make-optimal-filter expected-size fp-prob)]
    (loop [[l & lines] inp-seq
           res {}]
      (if-not l
        (loop [dps {}
               [k & ks] (keys res)]
          (cond
            (not k)  dps
            (> (res k) 1)  (recur (assoc dps k (res k)) ks)
            :else          (recur dps ks)))
        (let [[id phone-number] (.split l "\t")]
          (if-not (bloom/include? flt phone-number)
            (do
              (bloom/add! flt phone-number)
              (recur lines res))
            (do
              (recur lines
                     (assoc res
                       phone-number
                       (inc (res phone-number 1)))))))))))

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
  ;; est (* 9 200) 1800 seconds, or 30min for the full 2MM, which is better than an OOM

  ;; the full monty
  (time
   (pp/pprint
    (find-dupes-with-bloom-filter
      (ds/read-lines "phone-nums-with-lfsr-ids.txt")
      2000000 0.01)))

  )