# memo-lookahead-ttl

A Clojure library designed to provide a memo function that is gentle on the underlying cache hydration function.

The API behaves similarly to http://clojure.github.io/core.memoize/#clojure.core.memoize/ttl , but with some additional options.

eg:

```Clojure

;;

;;lets say you'd like to memoize something like this:
(defn some-long-running-error-prone-io-fn [time-to-run failrate seed]
    (println "eg: getting some data from a flaky, slow, fn, probably reaching into a database that probably doesn't like concurrent requests")
    (Thread/sleep (int (* time-to-run (rand))))
    (if (< failrate (rand))
       {:someresponse (+ time-to-run failrate seed) :freshness (new java.util.Date)}
       (throw (Exception. (str "Wow, this db is really bad, could't get a response for" time-to-run failrate seed )))
       ))


;;You really don't want to expose your program to this underlying hypothetical database
(some-long-running-error-prone-io-fn 1000 0.2 42)  ;; runs slowly and will fails 20% of the time.

;;create a memoize version of the function
(def memoed-slrep-io-fn (memo-lookahead-ttl some-long-running-error-prone-io-fn
                           {}   ;;initial seed for the cache
                           :mlttl/threshold 6000
                           :mlttl/refresh-threshold 3000
                           :mlttl/error-retries 2
                           :mlttl/max-consecutive-failed-retries 4
                           :mlttl/global-max-concurrent-requests 3))


(memoed-slrep-io-fn 1000 0.2 42)  ;; blocks until first successful response... may still throw if you are unlucky (3 consecutive failures)
(memoed-slrep-io-fn 1000 0.2 42)  ;; if above was successful, a cached answer will come quickly
;;;wait :mlttl/threshold 4500ms so that we are out of the refresh-threshold, but in :mlttl/threshold
(Thread/sleep 4500)
(memoed-slrep-io-fn 10000 0.2 42)  ;; You continue to get the old cached value, however, you also just triggered a run of some-long-running-error-prone-io-fn which will be attempted up to 2 times...
(memoed-slrep-io-fn 10000 0.2 42)  ;; Still getting old cached value, underlying function not done yet...
(Thread/sleep 4000)
(memoed-slrep-io-fn 10000 0.2 42)  ;; You should see an fresh response, unless you got unlucky, in which case there's probably another attempt are rehydration.
;; if you are very unlucky (or your database is down for long enough), after max-consecutive-failed-retries, which will spread out over at least 4 * :mlttl/refresh-threshold intervals, the memo function will return the underlying error. Next attempt will block as per the first invocation
;; In general, this is subject to :global-max-concurrent-requests which will prevent the underlying function from being swamped with concurrent requests.

```


memo-lookahead-ttl gets new data into a cache, before the cache time runs out, so that a refreshed value is ready to serve without the consumer waiting for the hydration.

Useful for tasks that can be memo'ed and that take a long time to run and where the response slowly evolves over time.

If the memo-lookahead-ttl is getting hits ongoing for a specific memo repose, responses will be relatively fresh, subject to the underlying function.


## Usage

```
(def memoed-your-fn (memo-lookahead-ttl your-fn
                           {}   ;;initial seed for the cache
                           :mlttl/threshold 6000                        ;; effectively maximum time a cache value is held before eviction and a forced refresh
                           :mlttl/refresh-threshold 3000                ;; time after which cache refresh will be attempted if requests are made
                           :mlttl/error-retries 2                       ;; # attempts of cache refresh before giving up (and falling back to previous cache), unless...
                           :mlttl/max-consecutive-failed-retries 4      ;; # consecutive failed retries before we rethrow underlying error,
                                                                        ;;     (for a specific request) and no longer extend the life of a cached result.
                           :mlttl/global-max-concurrent-requests 3      ;; maximum concurrent requests allowed to the underlying function, requests are queued up,
                                                                        ;;   but will be failed and not even attempted if they are not started within :mlttl/threshold
                 ))


```

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
