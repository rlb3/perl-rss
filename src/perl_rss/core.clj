(ns perl-rss.core
  (:use [clojure.java.io]
        [clojure.core]
        [perl-rss.modules])
  (:require [somnium.congomongo :as db]
            [clojure.data.json :as json]
            [postal.core :as mail]
            [clojure.string :as str]))

(def db (db/make-connection "perlrss" :host "127.0.0.1"))
(def settings (json/read-json (reader (file "settings.json"))))
(def modules (json/read-json (reader (file "modules.json"))))

(defn find-updated-modules []
  (first (filter not-empty (map find-perl-module modules))))

(def updated-modules (find-updated-modules))

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
          updated-modules))

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

(defn reset-seen []
  (db/with-mongo db
    (db/drop-coll! "seen")))

(defn send-email [email body]
  (mail/send-message {:from (:username settings)
                      :to email
                      :subject "Updated CPAN Modules"
                      :body body}))

(defn send-module-email [email u]
  (when (not-empty u)
    (let [mail-body (str/join "\n" (mail-message u))]
      (send-email email mail-body))))

(defn send-all-module-email [u]
  (map #(send-module-email % u)
       (map :email
              (db/with-mongo db
                (db/fetch :emails)))))

(defn clean-up [u]
  (when (not-empty u)
    (apply update-database u)))

(defn -main [& args]
  (let [unseen (unseen-modules)]
       (do
        (clean-up unseen)
        (send-all-module-email unseen))))
