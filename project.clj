(defproject org.clojars.ludothehun/memo-lookahead-ttl "0.1.0-SNAPSHOT"
  :description "A Clojure library designed to provide a memo function that is gentle on the underlying cache hydration function."
  :url "https://github.com/LudoTheHUN/memo-lookahead-ttl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/core.memoize "0.5.6"]])
