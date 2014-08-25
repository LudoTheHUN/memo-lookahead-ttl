(ns memo-lookahead-ttl.cache
  (:require [clojure.core.cache :as cca]))



(defn- key-killer
  [ttl expiry now]
  (let [ks (map key (filter #(> (- now (val %)) expiry) ttl))]
    #(apply dissoc % ks)))

(defn- key-lookup-killer
  ;; need to look over all cache items, and their ttlookup-ms times, remove any older then now, thier ttlookup time has expired
  [now]
  (fn [cachemap]
    (let [keep-nodes (map (fn [[i cacheitems]]
                            [i (filter #(<= % now)  (keys cacheitems))] ) cachemap)
          drop-nodes (map first (filter second
                                        (map (fn [[i cacheitems]]
                                               [i (empty? (filter #(> % now)  (keys cacheitems)))] ) cachemap)))]
      (apply dissoc
             (reduce (fn [xs [i cnodes]]
                       (update-in xs [i] (fn [x] (apply dissoc x cnodes))))
                     cachemap
                     keep-nodes)
             drop-nodes))))


(cca/defcache TTLlookaheadCache [cache ttl ttl-ms ttlookup-ms]
  cca/CacheProtocol
  (cca/lookup [this item]
              (let [ret (cca/lookup this item ::nope)]
                (when-not (= ret ::nope) ret)))
  (cca/lookup [this item not-found]  ;tri
              (let [cacheitems (get cache item)]
                 ;;(println "LOOK:cache: "this ":ttlmap" ttl "nownow:"  (System/currentTimeMillis))
                (if (empty? cacheitems)
                  not-found
                  (let [ttlookup-keys (keys cacheitems)]
                      (get cacheitems (apply min ttlookup-keys) not-found)))))
  (cca/has? [_ item]  ;;same as for ttl, used to indicate miss has to be called
            (let [t (get ttl item (- ttl-ms))]
              (< (- (System/currentTimeMillis)
                    t)
                 ttl-ms)))
  (cca/hit [this item] this)
  (cca/miss [this item result]
            (let [now  (System/currentTimeMillis)
                  kill-lookup-old (key-lookup-killer now)
                  kill-old (key-killer ttl ttl-ms now)]
              ;;(println "MISS:cache: "this ":ttlmap" ttl "nownow:" now)
              (TTLlookaheadCache. (assoc-in (kill-lookup-old cache) [item (+ now ttlookup-ms)] result)
                                  (assoc (kill-old ttl) item now)  ;;;WIP drives regydration only
                                  ttl-ms
                                  ttlookup-ms
                                  )))
  (cca/seed [_ base]
            (let [now (System/currentTimeMillis)]
              (TTLlookaheadCache. (into {} (for [x base] [(key x) {(+ now ttlookup-ms) (val x)}]))
                                  (into {} (for [x base] [(key x) now]))
                                  ttl-ms
                                  ttlookup-ms
                                  )))
  (cca/evict [_ k]
             (TTLlookaheadCache. (dissoc cache k)
                                 (dissoc ttl k)
                                 ttl-ms
                                 ttlookup-ms
                                 ))
  Object
  (toString [_]
            (str cache \, \space ttl \, \space ttl-ms)))


(defn ttl-lookahead-cache-factory
  "Returns a TTL lookahead cache with the cache and expiration-tables,  initialied to `base` --
   each with the same time-to-live and time-to-refresh.

   This function also allows an optional `:ttl` argument that defines the default
   time in milliseconds before we the cache has? test start to fail (which will then prompt miss hits) and
   `:ttlookup` argument that defines how long cache entries are used to serve lookup before being removed.
  "
  [base & {ttl :ttl ttlookup :ttlookup :or {ttl 3000 ttlookup 4000}}]  ; should always be responsive if time to make cache value is under a second, through will be hit once ever 3000ms if under constant demand
  {:pre [(number? ttl) (number? ttlookup) (<= 0 ttl) (<= 0 ttlookup)
         (map? base)]}
  (cca/seed (TTLlookaheadCache. {} {} ttl ttlookup) base))



(quote


 ((key-lookup-killer 3000) {:foo {123 :A 5000 :B }
                                :boo {3000 :C 3999 :C 4000 :D 500 :E}
                                :noo {23 :H 700 :I 500 :E}
                                :nn  {}})


 ((key-lookup-killer 30) {:foo {123 :A 5000 :B }
                                :boo {3000 :C 3999 :C 4000 :D 500 :E}
                                :noo {23 :H 700 :I 500 :E}
                                :nn  {}})


((key-lookup-killer 3000) {:a {}})



;(class (ttl-lookahead-cache-factory {} :ttl 1000))


(def lahcache
        (ttl-lookahead-cache-factory {} :ttl 5000 :ttlookup 8000))

  (assoc (cca/seed lahcache {:a 34 :z 34}) :b 3)


(cca/miss (cca/miss (cca/seed lahcache {:a 34}) :b 3) :f 3)


 (into {} (for [x {:a 1 :b 4}] [(key x) {32 (val x)}]))

(class lahcache)

#_(def lahcache
        (ttl-lookahead-cache-factory
           (cca/fifo-cache-factory {} :threshold 4)
         :ttl 5000))

#_(def lahcache
     (cca/fifo-cache-factory
        (ttl-lookahead-cache-factory  {} :ttl 5000 :ttlookup 6000) :threshold 2))


(cca/hit lahcache ["foo"])

(def midified_lahcache
       (cca/miss lahcache ["foo"] "boom"))

(cca/hit (-> midified_lahcache
    (assoc :c 42)
    (assoc :d 43)
    (assoc :e 44)) :e)


 (cca/hit (-> midified_lahcache
    (assoc :c 42)
    (assoc :d 43)
    ) :e)



(cca/lookup
   (-> midified_lahcache
    (assoc :c 42)
    (assoc :d 43)
    (assoc :e 44)) :c)

 (cca/miss
   (-> midified_lahcache
    (assoc :c 42)
    (assoc :d 43)
    (assoc :e 44)) :u 56)




(cca/fifo-cache-factory {} :threshold 2)


 (cca/lookup (let [ca1 (-> midified_lahcache
               (assoc :c 42)
               (assoc :d 43)
               (assoc :e 44))]
   (Thread/sleep 1000)
      (cca/miss ca1 :c 46)) :c)

  (loop [cach (ttl-lookahead-cache-factory {} :ttl 200 :ttlookup 300) anum 0]
     (Thread/sleep 10)
    (if (> anum 203)
      cach
      (recur
       ;;(cca/miss cach :c anum)
       (if (cca/has? cach :c)
           (cca/hit cach :c)
           (cca/miss cach :c anum))
         (+ 1 anum) )))  ;{:c {1408816932449 200, 1408816932247 180}}




(quote  ;;;drop this
;; has?   -- yes 'no' if time to refresh (:ttr) has run out
;; kill-old/key-killer need to kill when items turlly old only.
;; miss needs to update both :ttl and :ttr (rehydrate)
    ;; needs to put result in two places, one for reading for lookup, one for rehydration,. cl and cr
;; lookup - needs not to read from cl then before :ttr and from cr otherwise, miss will move reference from
      ;;cl to cr if within :ttr time

;;extend protocal to add 'rehydrate?'
;;create your own 'through' function that uses 'rehydrate?'
)



;;The simple solution!
;;;;; miss - places item into map (for each cache key) , map keys are :ttlookup times of item. Ie: how long should it serve as source of answers .forDigit
;;;;; lookup - looks at a cache items collection , takes the value from the oldest, non expired :ttlookup value (which in memo's case is a ref)


 )
