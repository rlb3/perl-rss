(ns perl-rss.modules
  (:use [clojure.java.io]
        [clojure.core])
  (:require [clojure.data.xml :as xml]
            [clojure.data.json :as json]
            [clojure [string :as str]]))

(def perl-url "http://search.cpan.org/uploads.rdf")

(defn- get-value [node]
  (first (:content node)))

(defn rss-title [entry]
  (get-value (first (filter #(= :title (:tag %))
                            (:content entry)))))

(defn get-new-perl-modules-list []
  (map #(rss-title %)
       (filter #(= :item (:tag %))
               (xml-seq (xml/parse (reader perl-url))))))

(defn get-new-perl-modules-map []
  (map
   (fn [v] (let [version (last v)
                 name (pop v)]
             {:name (str/join "::" name)
              :version version }))
   (map #(vec (str/split % #"-"))
        (get-new-perl-modules-list))))

(defn find-perl-module [s]
  (filter #(if (.equals s (:name %))
             true false)
          (get-new-perl-modules-map)))
