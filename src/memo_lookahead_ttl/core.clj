(ns memo-lookahead-ttl.core)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))


;;AIMs:
;;avoid Negative_cache   : http://en.wikipedia.org/wiki/Negative_cache
;;avoid Cache_stampede   : http://en.wikipedia.org/wiki/Cache_stampede
;;Pure                   : Use clojure primitives only
;;Protective             : Control number of concurrent calls to underlying function
;;ttl with fetchahead    : Refresh the cache value while still serving the cache
;;dynamic cache dynamics : WIP Allow cache characterystics to change dynamically, great for managing around downtimes of underlying resources of memo'ed function, without taking down the cache
;;                            ;;WIP optional automaitc reset of orignal cache dynmaics after non failing responses
;;exponential backoff    : up to set number of attempts before error is allowed to be to returned

;;Non aims:
;;Minimal Cache respone times
;;Consistancy in between different cache responses in the time domain, hits for different requests are on their own hydration timeline
;;Premptive prefetch  : If the cache function is not being used, it will not activiely hydrate ahead of time



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


(defn- now_ms [] (.getTime (new java.util.Date)))

(defn- promisize [x] (deliver (promise) x))

;;(promisize {:a 1})


;;(into {} (for [[k v] {:a 1}] [k (promisize v)]))




(def demo_coord_map
   {:work_slots_left 2
    :execution_queue  [[1 3]] ;initially a clojure.lang.PersistentQueue/EMPTY
    :cache {[1 2]
             {:cache_answer (deliver (promise) :42)
              :status :IDLE   ;;IDLE , RUNNING, ERROR
              :ttlt   (+ (now_ms) 100000)     ;;time to live till
              :ttrt   (+ (now_ms) 70000)   ;;time pas which to refresh
              :consecutive-failed-retries-left 4
               }}
    })




;;WIP need to do number of cache items accounting to decide if we should purge?
(defn purge_cache [coord_map]
 ;;looks over all cache itmes and removes those those that are :IDLE and :ttlt > now
  ;;TODO WIP.. not here consider adding a rand to choose if purge should happen, if it's expensive to purge with every call, this will lower the purge pressure
  (let [timenow (now_ms)
        args_array_to_filter (filter (fn [[k v]] (and (= (:status v) :IDLE) (> (now_ms) (:ttlt v)))) (get-in coord_map [:cache]))
        ;;WIP need to also delete if there are too many cache items ....
        ]
  (update-in coord_map [:cache] (fn [cache_maps]  (apply dissoc cache_maps (map first args_array_to_filter))))))
;;(purge_cache demo_coord_map)


(defn add_item_to_queue? [coord_map args_array]
  ;; Predicate function. Returns true if we shold do the work, false otherwise.
  ;;This should be down within a swap! together with the adding of items to queue to ensure correct state transition
  ;;Should do work only if we have no result for these args or (:status :IDLE and :ttrt > now )
  (cond (nil? (get-in coord_map [:cache args_array]))
          true
        (and (= (get-in coord_map [:cache args_array :status]) :IDLE) (< (get-in coord_map [:cache args_array :ttrt]) (now_ms)))
          true
        :else
          false))
;(add_item_to_queue? demo_coord_map [2 5])
;(add_item_to_queue? demo_coord_map [1 2])
;(add_item_to_queue? demo_coord_map [1 7])


(defn add_cache_map [coord_map args_array threshold refresh-threshold max-consecutive-failed-retries]
  ;;takes coordination_map, creates a new one ;;creates promise to deposit the answer if one doesn't already exist
  (update-in coord_map [:cache args_array] (fn [cache_map] (if (nil? cache_map)
                                                               (conj cache_map {:cache_answer  (promise)    ;we'll return this promise, and wait for it's delivery
                                                                                :status        :IDLE
                                                                                :ttlt          (+ (now_ms) threshold)
                                                                                :ttrt          (+ (now_ms) refresh-threshold)
                                                                                :consecutive-failed-retries-left max-consecutive-failed-retries
                                                                                })
                                                                cache_map))))
;(add_promise_to_cache demo_coord_map [1 5] 1000 2000 3)

(defn return_cached_promise [coord_map args_array]
  (get-in coord_map [:cache args_array :cache_answer]))
;;(return_cached_promise demo_coord_map [1 2])



(defn add_item_to_coordination_queue [coord_map args_array]
  ;;takes coordination_map, creates a new one, adds items to queue if :IDLE
  ;;WIP logic to determine if item should be added to queue will be done by a different fn dedicated to just this logic
(if (= (get-in coord_map [:cache args_array :status]) :IDLE)
     (-> coord_map
         (update-in  [:execution_queue] (fn [xq] (conj xq args_array)))
         (update-in  [:cache args_array] (fn [cache_map] (conj cache_map {:status :QUEUED}))))
     coord_map))

;(add_item_to_coordination_queue demo_coord_map [1 2])
;(add_item_to_coordination_queue {} [:a 1])
;(add_item_to_coordination_queue {:execution_queue clojure.lang.PersistentQueue/EMPTY}  [:a 1])


        ;;TODO atom to hold results
        ;;a way to check what needs to be evicted
        ;;running construct









(defn queue_poper! [coord_atom] ;;takes item off queue, return args to be done, :skip if there is nothing to do, does accouting of :work_slots_left
  (let [return_coord_map (swap! coord_atom
                                (fn [coord_map]
                                   (let [args_item (peek (:execution_queue coord_map))]
                                      (cond (nil? args_item)
                                              (assoc-in coord_map [:item-to-do-now] :skip)
                                            (> (coord_map :work_slots_left) 0)
                                              (-> coord_map
                                                (update-in [:execution_queue] pop)
                                                (update-in [:work_slots_left] dec)
                                                (assoc-in  [:item-to-do-now] args_item))
                                            :else
                                                (assoc-in coord_map [:item-to-do-now] :skip)
                                            ))))]
    (:item-to-do-now return_coord_map )))

;(let [a (atom demo_coord_map)] (queue_poper! a))
;(let [a (atom demo_coord_map)] (queue_poper! a) a)

;;Now we do the work.... and....

;;TODO need a function for Exception from underlying function (Exception catching), will count number of errors and back off the :ttrt, unless max errors is reached, a retry will happen only if value is requested again after a :ttrt



(defn deliver_a_result! [coord_atom args_array result threshold refresh-threshold max-consecutive-failed-retries]
  ;;switches in deliverd promis for some args_array, does accounting of :work_slots_left
  (swap! coord_atom (fn [coord_map]
      (-> coord_map
           (update-in [:cache args_array] (fn [cache_map]
                                             (let  [p (:cache_answer cache_map)]
                                                   {:cache_answer (cond (nil? p)   ;;should never happen since we should never clean out a cache unless it is :IDLE
                                                                          (deliver (promise) result)
                                                                        (not (realized? p))    ;;NOTE: this is not pure, but appropriate??
                                                                             ;;someone could realize the promise between this test and the delivery attempt, resulting in nil being returned and put into :cache_answer
                                                                           (do (deliver p result) ;;need to ensure the nil is never returned
                                                                             p)
                                                                        :else
                                                                          (deliver (promise) result))   ;; if there was a STM retry of this swap! and the promise was already delivered, we'll create another promise and deliver it immediately
                                                   :status        :IDLE
                                                   :ttlt          (+ (now_ms) threshold)
                                                   :ttrt          (+ (now_ms) refresh-threshold)
                                                   :consecutive-failed-retries-left max-consecutive-failed-retries}
                                                  )))
           (update-in [:work_slots_left] inc)))))
;(deliver_a_result! (atom demo_coord_map) [1 2] 46  15000 10000 3)
;(deliver_a_result! (atom demo_coord_map) [1 5] 45  15000 10000 3)   ;;no where to put answer...


(defn deliver_error [coord_atom args_array caught_exception_result    threshold refresh-threshold max-consecutive-failed-retries]
  :WIP
  ;; refactor error countg? if less the max number of errors, keep old answer, inc consecutive error count, move ttl back with exponential backoff,
     ;; else, deliver the error and reset ???
  )

(let [caught_exception
        (try (throw (Exception. "foo"))
            (catch Exception e e))]
  @(deliver (promise) caught_exception))

;(exception?)







;;WIP think through the retries case(s)....


(defn foo [& args] (vec args))







;;TODO
(defn memo-lookahead-ttl [f init_cache & setupargs]
  (let [setupargs_map (apply hash-map setupargs)
        {:keys [threshold refresh-threshold error-retries max-consecutive-failed-retries global-max-concurrent-requests]
         :or {threshold 0
              refresh-threshold 0
              error-retries 0
              max-consecutive-failed-retries 0
              global-max-concurrent-requests 1}}  setupargs_map

        ;;WIP CONFIG_ATOM configuration atom, allow caching strategy to change dynamically
        coordination_atom  (atom {:work_slots_left global-max-concurrent-requests
                                  :execution_queue clojure.lang.PersistentQueue/EMPTY
                                  :cache (into {} (for [[k v] init_cache] [k {:cache_answer (promisize v)
                                                                              :status       :IDLE
                                                                              :ttlt         (+ (now_ms) threshold)
                                                                              :ttrt         (+ (now_ms) refresh-threshold)
                                                                              ::consecutive-failed-retries-left max-consecutive-failed-retries}])) })
        ]
 (fn [& argsn]
   (let [args (vec argsn)   ;;; Carefull with missig args, they should be represented as [] not nil, especially on the queue
         ;;result_promise (promise)
         ;;c_atom_before   (swap! coordination_atom (fn [cache] ))  ;;put primis in if it's appropriate to do so, in the :next spot if main spot is taken, or onto the queue

         ;WIP CONFIG_ATOM here acertains what the config is right now
         ]
   #_{:number_currently_running 0
    :execution_queue  [[1 2 3] [:a :b] ] ;initially a clojure.lang.PersistentQueue/EMPTY
    :cache {args
             {:cache_answer attempt-promise
              :cache_next_answer attempt-promise
              :status :RUNNING   ;;IDLE , RUNNING, ERROR
              :ttlt (+ (now_ms) threshold)     ;;time to live till
              :ttrt (+ (now_ms) refresh-threshold)   ;;time pas which to refresh
              :consecutive-failed-retries-left max-consecutive-failed-retries
               }}
    }))
  ))




(peek (conj clojure.lang.PersistentQueue/EMPTY {:a 1}))
(peek (pop clojure.lang.PersistentQueue/EMPTY))
(swap! (atom {:a 1})  (fn [x] (conj x [:b 3])))




#_(comment


       ;;  attempt-fut     (future (try  (apply f args)           ;;pacify the f
       ;;                                (catch Exception e e)))

;derf chache_atom, if answer available and no need to run, return with result
;  else . if need to run but answer available, return with answer, but beforehand start a future to do the run + create a primis into which answer will be placed... (via which all other consumers will block untill this is delivered)
; if no answer available, do the run directly (return with answer at the end)
;
;  doing a run
;  via uuid
;(swap!  aquire lock on answer for updates (no one else can be running right now that thing)  )
;  if you have the lock...
;  swap! add to the queue if no slots free, if free slots, pop into to running set
;
;  star looping:
;   deref queue+runnig set
;   if in running uuid set, run
;      else test if we've timed out on waiting , sleep or release lock and throw attempt time out
;
;    run...
;     actually do the (apply f args) pacified
;     look if results is realized  wait till it is.
(swap! start running.... add to the running set, pop from running queue)
;; do actial
(if sucessfull, switch in the result )

 (str (java.util.UUID/randomUUID))




(def memoed-identity (memo-lookahead-ttl identity {} :threshold 132))

(memoed-identity 9)


(def memoed-slrep-io-fn (memo-lookahead-ttl some-long-running-error-prone-io-fn {} :threshold 132))

(def result1 (memoed-slrep-io-fn 10000 0.2 42))



(= (promise) (promise) )





(def a-promise (promise))
(realized? a-promise)
(future (do (println "got back a promis" @a-promise ) (println "got" @a-promise) ))
(deliver a-promise :fred4)



(def timedout-fut   (future (Thread/sleep 10000)))
(realized? timedout-fut)
(deref timedout-fut 100 :timeout! )



(now_ms)

(def example_cache_data {[1 2 3]  {:cache_answer 6
                                   :keep_until (now_ms)
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

)
