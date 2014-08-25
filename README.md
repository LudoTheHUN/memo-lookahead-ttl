# memo-lookahead-ttl

A Clojure library designed to provide a memo function that is gentle on the underlying cache hydration function.

The API behaves similarly to http://clojure.github.io/core.memoize/#clojure.core.memoize/ttl , but with some additional options.

## Usage

```Clojure

(ns your-ns
  (:require [memo-lookahead-ttl.memoize :as :tmemo]))

(defn your-function [] (Thread/sleep 10) (System/currentTimeMillis) ))

(def your-memoed-function (tmemo/ttl-lookahead your-function {} :ttl 80 :ttlookup 100))


```

As long as your-memoed-function is called at least every 80ms, it will always return the answer imediately (other then the first time) and return the epoch time no later then 100ms ago.

`ttl-lookahead` is a drop in replacement for `clojure.core.memoize/ttl`

## Why?

The use case for this behavior is to place it in front of a resource that slowly changes over time (eg: slowly changing web content), but takes (in our example no more then 20ms, if we want to protent the caller from a wait) a long time to return. As long as there are conitnuous hits to the memo function, we will return quickly with a value at most :ttlookup ms old.

## Notes

:ttlookup should be more then :ttl (else we will not have pre-emptively hydration)

:ttlookup should be not be more then ttl*2 (else we will hold more then 2 values in the cache and needlessly delay visibility of updated values


## License

Copyright © 2014 Ludwik Grodzki, All rights reserved

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
