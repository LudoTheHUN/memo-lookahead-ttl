(ns memo-lookahead-ttl.memoize-test
  (:require [clojure.test :refer :all]
            [memo-lookahead-ttl.memoize :refer :all]
            ))


(deftest basic-memo-tests

(def memoedfn1 (ttl-lookahead identity {}))

(is (= (memoedfn1 3) 3))
(is (= @(memoedfn1 (future (+ 1 2)))  )   )

)


(defn slow_fn1 [x]
  (let [calltime (System/currentTimeMillis)]
    (do ;(println "calling sleep for "x "with call time " calltime)
      (Thread/sleep x)
      ;(println "done sleeping for " x "with call time " calltime)
      {:answer x :nowis  calltime})
    ))


(def memoedfn3 (ttl-lookahead slow_fn1 {} :ttl 80 :ttlookup 100))

(deftest memo_timing_tests

  (let [time_at_start (System/currentTimeMillis)
        dowork (loop [anum 0]
                 (Thread/sleep 1)
                 (if (> anum 1000)
                   anum
                   (do (memoedfn3  10)
                     (recur
                      ;;(cca/miss cach :c anum)
                      (+ 1 anum) ))))
        time_at_end (System/currentTimeMillis)
        ]
    (is (< (- time_at_end time_at_start) 1200))    ; other then initially, we never wait for the 10ms slow_fn1
    (is (=  (:answer(memoedfn3  10)) 10))
    )

  )





