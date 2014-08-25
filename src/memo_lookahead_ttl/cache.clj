(ns memo-lookahead-ttl.cache
  (:require [clojure.core.cache :as cca]))


;;A simple cache that enables a lookahead ttl memo.
;;;;; miss - places item into map (for each cache key) , map keys are :ttlookup times of item. Ie: until when is this the value to lookup
;;;;; lookup - looks at a cache items collection , takes the value from the oldest :ttlookup key
;;;;; when we use the cache to build a memo function, the value stored will be a future, so that hopefully, by the time the cache value is deref'ed during lookup, the future will already be realized,
     ;;;hence the memo function can potentially show no downtime to the consumer (if the cache it hit regularly).


(defn key-killer
  [ttl expiry now]
  (let [ks (map key (filter #(> (- now (val %)) expiry) ttl))]
    #(apply dissoc % ks)))

(defn key-lookup-killer
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
                ;(when-not (= ret :memo-lookahead-ttl.cache/nope) ret)   ;; this was returning nil, when ::nope
                ret
                ))
  (cca/lookup [this item not-found]  ;tri
              (let [cacheitems (get cache item)]
                ;;(println "LOOK:cache: "this ":ttlmap" ttl "nownow:"  (System/currentTimeMillis))  ;;debug line
                (if (empty? cacheitems)
                  not-found
                  (let [ttlookup-keys (keys cacheitems)]
                    (get cacheitems (apply min ttlookup-keys) not-found)))))
  ;;Note that this cache 'lies' when called with has? Cache may have a value it can lookup, even though it responds false to has?
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
              ;;(println "MISS:cache: "this ":ttlmap" ttl "nownow:" now)  ;;debug line
              (TTLlookaheadCache. (assoc-in (kill-lookup-old cache) [item (+ now ttlookup-ms)] result)
                                  (assoc (kill-old ttl) item now)  ;;;WIP drives regydration only
                                  ttl-ms
                                  ttlookup-ms)))
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
