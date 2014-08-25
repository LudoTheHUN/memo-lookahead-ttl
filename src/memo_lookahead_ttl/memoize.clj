(ns memo-lookahead-ttl.memoize
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
  "a memo function that uses the TTLlookaheadCache to give a pre-emptively hydrating behaviour.
   Note that :ttlookup should be more then :ttl (else we will not have pre-emptively hydration)
   and not be more then ttl*2 (else we will hold more then 2 values in the cache and needlessly delay visibility of updated values"
  ([f base & {ttl :ttl ttlookup :ttlookup :or {ttl 3000 ttlookup 4000}}]

    (;;mem/build-memoizer
     build-memoizer-eager
       #(mem/PluggableMemoization. %1 (ttlkc/ttl-lookahead-cache-factory %4 :ttl %2 :ttlookup %3))
       f
       ttl
       ttlookup
       base)))





