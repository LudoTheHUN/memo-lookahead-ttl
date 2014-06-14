(ns memo-lookahead-ttl.core)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))


;;AIMs:
;;avoid Negative_cache: http://en.wikipedia.org/wiki/Negative_cache
;;avoid Cache_stampede: http://en.wikipedia.org/wiki/Cache_stampede
;;Pure                : Use clojure primitives only
;;Protective          : Control number of concurrent calls to underlying function
;;ttl with lookahead  :

;;Non aims:
;;Cache respone times
;;Consistancy in between different cache responses in the time domain, hits for different requests are on their own hydration timeline
;;Premptive prefetch  : If the cache function is not being used, it will not activiely hydrate

(defn some-long-running-error-prone-io-fn [time-to-run failrate seed]
    (println "a flaky, slow fn")
    (Thread/sleep (int (* time-to-run (rand))))
    (if (< failrate (rand))
       {:someresponse (+ time-to-run failrate seed) :freshness (new java.util.Date)}
       (throw (Exception. (str "Wow, this db is really bad, could't get a response for" time-to-run failrate seed )))
       ))



#_(some-long-running-error-prone-io-fn 1000 0.001 42)


;;TODO evication
;;pushing futures around
;;cache data

#_(def memoed-your-fn (memo-lookahead-ttl your-fn
                           {}   ;;initial seed for the cache
                           :mlttl/threshold 6000                        ;; effectively maximum time a cache value is held before eviction and a forced refresh
                           :mlttl/refresh-threshold 3000                ;; time after which cache refresh will be attempted if requests are made
                           :mlttl/error-retries 2                       ;; # attempts of cache refresh before giving up (and falling back to previous cache), unless...
                           :mlttl/max-consecutive-failed-retries 4      ;; # consecutive failed retries before we rethrow underlying error,
                                                                        ;;     (for a specific request) and no longer extend the life of a cached result.
                           :mlttl/global-max-concurrent-requests 3      ;; maximum concurrent requests allowed to the underlying function, requests are queued up,
                                                                        ;;   but will be failed and not even attempted if they are not started within :mlttl/threshold
                 ))



{:d 3

 }


(defn memo-lookahead-ttl [f init_cache & args]
  (let [{threshold                      :mlttl/threshold
         refresh-threshold              :mlttl/refresh-threshold
         error-retries                  :mlttl/error-retries
         max-consecutive-failed-retries :mlttl/max-consecutive-failed-retries
         global-max-concurrent-requests :mlttl/global-max-concurrent-requests} args
        ;;TODO atom to hold results
        ;;a way to check what needs to be evicted
        ;;running construct


        ]

  threshold
  ))






(memo-lookahead-ttl :a {}  :mlttl/threshold 35)





