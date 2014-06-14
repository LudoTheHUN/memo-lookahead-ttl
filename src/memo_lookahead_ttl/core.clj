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
    ;;(println "a flaky, slow fn")
    (Thread/sleep (int (* time-to-run (rand))))
    (if (< failrate (rand))
       {:someresponse (+ time-to-run failrate seed) :freshness (new java.util.Date)}
       (throw (Exception. (str "KaBoom!" time-to-run failrate seed )))
       ))



#_(some-long-running-error-prone-io-fn 1000 0.001 42)


;;TODO evication
;;pushing futures around
;;cache data
;;TODO use STM to ensure consitant behaviour


(defn memo-lookahead-ttl [f init_cache & setupargs]
  (let [setupargs_map (apply hash-map setupargs)
        {:keys [threshold refresh-threshold error-retries max-consecutive-failed-retries global-max-concurrent-requests]
         :or {threshold 0
              refresh-threshold 0
              error-retries 0
              max-consecutive-failed-retries 0
              global-max-concurrent-requests 1}}  setupargs_map
         chache_atom                    (atom init_cache) ;;WRONG vs memo API... we store metadata here too
        ;;TODO atom to hold results
        ;;a way to check what needs to be evicted
        ;;running construct

        ]
 (fn [& args]
   (let [c_atom_before   @chache_atom

         attempt-fut     (future (try  (apply f args)           ;;pacify the f
                                       (catch Exception e e)))

         ]

   {:number_currently_running
    :execution_queue  [{:retries_left 3 :args [1 2 3]}]
    :cache {args     {:cache_answer attempt-fut
               :status :RUNNING   ;;IDLE , RUNNING, ERROR
               :epoch_keep_at_most_until (+ (.getTime (new java.util.Date)) threshold)
               :epoch_refresh_after     (+ (.getTime (new java.util.Date)) refresh-threshold)
               :retries_left                    error-retries
               :consecutive-failed-retries-left max-consecutive-failed-retries

               }}
    }))
  ))


(comment
derf chache_atom, if answer available and no need to run, return with result
  else . if need to run but answer available, return with answer, but beforehand start a future to do the run
 if no answer available, do the run directly (return with answer at the end)

  doing a run:
  via uuid
(swap!  aquire lock on answer for updates (no one else can be running right now that thing)  )
  if you have the lock...
  swap! add to the queue if no slots free, if free slots, pop into to running set

  star looping:
   deref queue+runnig set
   if in running uuid set, run
      else test if we've timed out on waiting , sleep or release lock and throw attempt time out

    run...
     actually do the (apply f args) pacified
     look if results is realized  wait till it is.
(swap! start running.... add to the running set, pop from running queue)
;; do actial
(if sucessfull, switch in the result )

 (str (java.util.UUID/randomUUID))
)



(def memoed-identity (memo-lookahead-ttl identity {} :threshold 132))

(memoed-identity 9)


(def memoed-slrep-io-fn (memo-lookahead-ttl some-long-running-error-prone-io-fn {} :threshold 132))

(def result1 (memoed-slrep-io-fn 10000 0.2 42))




(def a-promise (promise))
(realized? a-promise)
(future (do (println "got back a promis" @a-promise ) (println "got" @a-promise) ))
(deliver a-promise :fred3)



(def timedout-fut   (future (Thread/sleep 10000)))
(realized? timedout-fut)
(deref timedout-fut 100 :timeout! )



(.getTime (new java.util.Date))

(def example_cache_data {[1 2 3]  {:cache_answer 6
                                   :keep_until (.getTime (new java.util.Date))
                                   }
                         })


(def fut (future
           (try
               (throw (Exception. (str "Eeee Owhhh")))
             (catch Exception e {:BOOM e})

           )))

#_(throw (:BOOM @fut))

(def fut (future
           (try
               (throw (Exception. (str "Eeee Owhhh")))
             (catch Exception e e)
           )))


(class @fut)



(memo-lookahead-ttl :a {}  :threshold 35)



#_(def memoed-your-fn (memo-lookahead-ttl your-fn
                           {}   ;;initial seed for the cache
                           :threshold 6000                        ;; effectively maximum time a cache value is held before eviction and a forced refresh
                           :refresh-threshold 3000                ;; time after which cache refresh will be attempted if requests are made
                           :error-retries 2                       ;; # attempts of cache refresh before giving up (and falling back to previous cache), unless...
                           :max-consecutive-failed-retries 4      ;; # consecutive failed retries before we rethrow underlying error,
                                                                        ;;     (for a specific request) and no longer extend the life of a cached result.
                           :global-max-concurrent-requests 3      ;; maximum concurrent requests allowed to the underlying function, requests are queued up,
                                                                        ;;   but will be failed and not even attempted if they are not started within :threshold
                 ))


