(ns memo-lookahead-ttl.cache
  (:require [memo-lookahead-ttl.cache :as ttlkc]
            [clojure.core.memoize :as mem]
            [clojure.core.cache :as cca])
  (:import [clojure.core.memoize PluggableMemoization]
            ))


(defn through-eager* [cache f item]
  "The basic hit/miss logic for the cache system based on `core.cache/through`.
  Clojure futures are used to eagerly evaluate and hold the cache value."
  (clojure.core.cache/through
   ;; (fn [f a] (d-lay #(f a)))     ;;;;original from clojure.core.memoize
    (fn [f a] (future (f a)))
    #(clojure.core/apply f %)
    cache
    item))

(defn build-memoizer-eager
  "Builds a function that given a function, returns a pluggable memoized
   version of it.  `build-memoizer` Takes a cache factory function, a function
   to memoize, and the arguments to the factory.  At least one of those
   functions should be the function to be memoized."
  ([cache-factory f & args]
     (let [cache (atom (apply cache-factory f args))]
       (with-meta
        (fn [& args]
          (let [cs  (swap! cache through-eager* f args)  ;;using through-eager*
                val (clojure.core.cache/lookup cs args)]
            ;; The assumption here is that if what we got
            ;; from the cache was non-nil, then we can dereference
            ;; it.  core.memo currently wraps all of its values in
            ;; a `delay`, but we wrap it in a future.
            (and val @val)))
        {::cache cache
         ::original f}))))



(defn ttl-lookahead
  "a memo function that uses the TTLlookaheadCache to give a pre-emptively hydrating behaviour"
  ([f base & {ttl :ttl ttlookup :ttlookup :or {ttl 3000 ttlookup 4000}}]

    (;;mem/build-memoizer
     build-memoizer-eager
       #(mem/PluggableMemoization. %1 (ttlkc/ttl-lookahead-cache-factory %4 :ttl %2 :ttlookup %3))
       f
       ttl
       ttlookup
       base)))



(quote

(def memoedfn (ttl-lookahead identity {}))

(def memoedfn (ttl-lookahead identity {} :ttl 1000 :ttlookup 5000))

;(memoedfn 3)


(defn slow_fn [x]
  (let [calltime (System/currentTimeMillis)]
    (do (println "calling sleep for "x "with call time " calltime)
      (Thread/sleep x)
      (println "done sleeping for " x "with call time " calltime)
      {:answer x :nowis  calltime})
    ))

(def memoedfnslow (ttl-lookahead slow_fn {} :ttl 3000 :ttlookup 4000))

(time (memoedfnslow 1000))
(time (memoedfnslow 1005))

(def memoedfnslow2 (ttl-lookahead slow_fn {:a 232} :ttl 4000 :ttlookup 10000))
(time (memoedfnslow2 1005))


;((mem/memo-unwrap memoedfnslow)  100)

(loop [anum 0]
     (Thread/sleep 100)
    (if (> anum 100)
      anum
      (do (memoedfnslow 1000)
      (recur
       ;;(cca/miss cach :c anum)
         (+ 1 anum) ))))  ;{:c {1408816932449 200, 1408816932247 180}}



)





