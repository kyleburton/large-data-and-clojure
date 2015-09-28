(ns data-talk.stream-sampling
  (:require
   [clj-etl-utils.sequences :refer [random-sample-seq]]
   [schema.core             :as s]
   [clojure.java.io         :as io]))

;; Given a large data set (too large to fit in RAM),
;; we know the cardinality (number of entries)
;; and wish to take a random sampling.

;; 

(defonce dict-file "/usr/share/dict/words")
(defonce emails-file "data/emails.txt")

(def num-lines (memoize (fn [fname]
                          (with-open [rdr (io/reader fname)]
                            (count (line-seq rdr))))))

(s/defn sample-lines [fname :- s/Str sample-size :- s/Num]
  (with-open [rdr (io/reader fname)]
    (doall
     (random-sample-seq
      (line-seq
       rdr)
      (num-lines fname)
      sample-size))))


(comment

  (time (num-lines dict-file))

  (time (num-lines emails-file))

  (sample-lines dict-file 10)
  (sample-lines emails-file 10)

)
