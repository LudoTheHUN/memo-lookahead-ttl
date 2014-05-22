(ns memo-lookahead-ttl.core
  (:require [clojure.core.memoize :as memo]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))






(def timeouts (atom {}))




(defn foofn1 [x y] x)
(defn foofn2 [x y] x)

;checks that a memo function is still itself after it's gotten some more cache.

(defn lookah [fn1 fn2 timeout & ops]
  (do

    ;;self removeing from atom
    ;; if fn1 caches is there, get it's answer...
    (if (@timeouts [fn1 fn2 ops])
      ;;; if timeout is there, move along, if it's not, put the other function that is not available on a future for it populate it's cache
        (swap! timeouts (fn [x] (if (x [fn1 fn2 ops])
                                    (do (println "still hot") x)
                                    (conj x [[fn1 fn2 ops] (future (do
                                                               (Thread/sleep timeout)
                                                               (swap! timeouts  (fn [y] (dissoc y [fn1 fn2 ops])))))
                                         ]))))

   ;; [fn1 fn2 ops (= fn1 fn2) (= fn1 fn1)]
    (apply foofn1 ops)
  ))


(lookah foofn1 foofn2 10000 :f :c)

timeouts




