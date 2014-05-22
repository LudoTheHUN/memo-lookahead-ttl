# memo-lookahead-ttl

A Clojure library designed to provide a memo function that gets new data into a cache, before the cache time runs out. Usefull for tasks that can be memo'ed and that take a long time. If the cache is getting constant hits, there will always be a recent cache that can be hit (unless the task takes too long), via the long running task being in the background ahead of the ttl timeout.

The function has a two part ttl, an initial :hot phase, during which it's serving it's internal cache, and a :cool phase, during which it still serves from the cache while it start a background task to refresh a seconday cache, before the primary runs out. This way we can avoid long time intervals where the cache is not available as is the case with the vanila memo/ttl

## Usage

FIXME

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
