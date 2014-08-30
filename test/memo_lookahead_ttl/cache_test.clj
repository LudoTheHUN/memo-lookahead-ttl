(ns memo-lookahead-ttl.cache-test
  (:require [clojure.test :refer :all]
            [memo-lookahead-ttl.cache :refer :all]
            [clojure.core.cache :as cca]))


(deftest key-lookup-killer-tests

  (is (=
       ((key-lookup-killer 3000) {:foo {123 :A 5000 :B }
                                  :boo {3000 :C 3999 :C 4000 :D 500 :E}
                                  :noo {23 :H 700 :I 500 :E}
                                  :nn  {}})
       {:foo {5000 :B}, :boo {3999 :C, 4000 :D}}))


  (is (=
       ((key-lookup-killer 30) {:foo {123 :A 5000 :B }
                                :boo {3000 :C 3999 :C 4000 :D 500 :E}
                                :noo {23 :H 700 :I 500 :E}
                                :nn  {}})
       {:foo {123 :A, 5000 :B}, :boo {3000 :C, 3999 :C, 4000 :D, 500 :E}, :noo {700 :I, 500 :E}}
       ))


  (is (=
       ((key-lookup-killer 3000) {:a {}}))))


(deftest cache-tests

  (def lahcache
    (ttl-lookahead-cache-factory {} :ttl 200 :ttlookup 300))

  (is (= lahcache {}))
  (is (= (class lahcache) memo_lookahead_ttl.cache.TTLlookaheadCache))

  (let [seededcache (cca/seed lahcache {:a 34 :z 35})]
    (is (= (cca/lookup seededcache :a) 34))
    (is (= (cca/lookup seededcache :z) 35))
    (is (= lahcache {}))
    (Thread/sleep 400)
    (let [postmisscache (cca/miss seededcache :b 31)]
      (is (= (cca/lookup seededcache :a) 34))  ; cache is immutable, no change
      (is (= (cca/lookup seededcache :z) 35))  ; cache is immutable, no change
      (is (= (cca/lookup postmisscache :b) 31))  ; new missed vlaue available
      (is (= (cca/lookup postmisscache :a) :memo-lookahead-ttl.cache/nope))  ;old cache value have timed out, missing
      (is (= (cca/lookup postmisscache :z) :memo-lookahead-ttl.cache/nope))  ;old cache value have timed out, missing
      (is (= (cca/lookup postmisscache :z "MISSING!") "MISSING!"))           ;optional missing value works
      (is (= (cca/lookup (assoc postmisscache  :c 42) :c 42))))))

  (deftest ment-cache-hits-tests-flaky
    ;;note this test can be flaky
    (let [posthitcahe (loop [cach (ttl-lookahead-cache-factory {} :ttl 200 :ttlookup 300) anum 0]
                        (Thread/sleep 10)
                        (if (> anum 45)
                          cach
                          (do (is (contains? #{:memo-lookahead-ttl.cache/nope 0 20 40} (cca/lookup cach :c)))
                            (recur
                             ;;(cca/miss cach :c anum)

                             (if (cca/has? cach :c)
                               (cca/hit cach :c)
                               (cca/miss cach :c anum))
                             (+ 1 anum) ))))
          ]
      ; cache has 2 values for the single key, from 20'th and 40'th loop only
      (is (=  (second (first (second (first posthitcahe)))) 40))
      (is (=  (second (second (second (first posthitcahe)))) 20))
      ; but is still serving the value from the 20's loop since it has not :ttlookup expired yet.
      (is (=  (cca/lookup posthitcahe :c) 20))))



  (deftest cache-compositon

    (def combined_cache1 (-> {:a 2 :b 3 :c 4}
                             (cca/fifo-cache-factory :threshold 2)
                             (ttl-lookahead-cache-factory :ttl 1000 :ttlookup 1500)
                             ))


    (def combined_cache2 (-> {:a 2 :b 3 :c 4}
                             (ttl-lookahead-cache-factory :ttl 1000 :ttlookup 1500)
                             (cca/fifo-cache-factory :threshold 2)
                             ))


    (cca/lookup combined_cache1 :a)
    (cca/lookup combined_cache2 :a)

    (cca/lookup combined_cache1 :b)
    (cca/lookup combined_cache2 :b)

    (cca/miss combined_cache1 :d 5)
    (cca/miss combined_cache2 :d 5)

    (cca/miss (cca/miss combined_cache1 :d 5) :d 6)
    (cca/miss (cca/miss combined_cache2 :d 5) :d 6)


    (cca/miss (cca/miss combined_cache1 :d 5) :e 6)
    (cca/miss (cca/miss combined_cache2 :d 5) :e 6)


    ;; fifi behaviour is effectively overridden by ttl-lookahead, so this composition is fruitless
    (is (= (count  (cca/miss (cca/miss (cca/miss combined_cache1 :d 5) :e 6) :f 7)) 5))  ;;Note this will only be true until :ttlookup timeout


    ;; fifi behaviour is maintained while we also have ttl-lookahead behaviour
    (let [ca (cca/miss (cca/miss (cca/miss combined_cache2 :d 5) :e 6) :f 7)]
             (is (= (count ca) 2))    ;; fifo has limited us to 2
            (Thread/sleep 2000)
             (is (= (count (cca/miss ca :q 9))) 1)   )  ;;TTL has removed value




    (def combined_cache_a1  (-> {:a 2 :b 3 :c 4} (cca/fifo-cache-factory :threshold 2)))
    (def combined_cache_b1  (ttl-lookahead-cache-factory combined_cache_a1 :ttl 2000 :ttlookup 3000))

    (def combined_cache_a2  (-> {:a 2 :b 3 :c 4} (ttl-lookahead-cache-factory :ttl 2000 :ttlookup 3000)))
    (def combined_cache_b2  (cca/fifo-cache-factory combined_cache_a2 :threshold 2))



    (cca/lookup combined_cache_b1 :a)
    combined_cache_a1
    combined_cache_b1

    (cca/lookup combined_cache_b2 :a)
    combined_cache_a2
    combined_cache_b2

  ;;; Question... is combined_cache_b1 going to become large as we hit it (defeating the fifo it has been composed with?)
  ;;; A: Yes, combined_cache_b1 will keep growing (as far as :ttl lets it be) , but combined_cache_b2 will always be size 2 capped

    )



