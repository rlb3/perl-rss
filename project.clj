(defproject perl-rss "1.0.0-SNAPSHOT"
  :description "Perl RSS Checker"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.xml "0.0.3"]
                 [org.clojure/data.json "0.1.2"]
                 [congomongo "0.1.8"]
                 [clj-mail "0.1.5"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]]
  :main perl-rss.core)
