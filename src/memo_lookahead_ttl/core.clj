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


{:d 3

 }


