(ns perl-rss.core
  (:use [clojure.java.io]
        [clojure.core])
  (:require [clojure.data.xml :as xml]
            [clojure.data.json :as json]
            [clojure [string :as str]]
            [somnium.congomongo :as db]
            [clj-mail.core :as mail]))

(def db (db/make-connection "perlrss" :host "127.0.0.1"))

(def settings (json/read-json (reader (file "settings.json"))))

(defn make-mail-session []
  (mail/mk-Sess {:username (:username settings)
                         :pass (:pass settings)
                         :ssl? true
                         :in-host (:in-host settings)
                         :in-protocol "imap"
                         :in-port 993
                         :out-host (:out-host settings)
                         :out-protocol "smtp"
                         :out-port 465}))

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

(defn modules []
  (map :name (db/with-mongo db
               (db/fetch :modules))))

(defn find-updated-modules []
  (first (filter not-empty (map find-perl-module (modules)))))

(defn seen-modules [module-map]
  (let [name (:name module-map)
        version (:version module-map)]
    (first (db/with-mongo db
             (db/fetch :seen
                       :where {:name name
                               :version version})))))

(defn unseen-modules []
  (filter #(if (empty? (seen-modules %))
             true false)
          (find-updated-modules)))

(defn update-database [map]
  (db/with-mongo db
    (db/insert! :seen {:name (:name map)
                       :version (:version map)})))

(defn mail-message [modules]
  (map (fn [m]
         (let [name (:name m)
               version (:version m)
               matcher (re-matcher #"_" version)
               dev (re-find matcher)]
           (str (format "%s-%s" name version) (if dev " DEVELEMENT VERSION\n" "")))) modules))

(defn reset-seen [] (db/with-mongo db
                      (db/drop-coll! "seen")))

(defn send-email []
  (when (not-empty (unseen-modules))
    (let [mail-body (apply concat (mail-message (unseen-modules)))
          sess (make-mail-session)]
      (mail/send-msg sess (mail/text-msg sess {:to-coll (:to-coll settings)
                                               :subject "Updated CPAN Modules"
                                               :body (apply str mail-body)}))
      (apply update-database (unseen-modules))))
  (db/close-connection db))

(defn -main [& args] (send-email))


(comment
  settings
  (reset-seen)
  (get-new-perl-modules-map)
  (find-updated-modules)
  (mail-message (unseen-modules))
  (-main)
  )
