(ns perl-rss.core
  (:gen-class)
  (:use [clojure.java.io]
        [clojure.core]
        [perl-rss.modules])
  (:require [clojure.data.json :as json]
            [postal.core :as mail]
            [clojure.string :as str]))

(def settings (json/read-json (reader (file "settings.json"))))
(def modules (json/read-json (reader (file "modules.json"))))
(def seen (json/read-json (reader (file "seen.json"))))
(def emails (json/read-json (reader (file "emails.json"))))

(defn find-updated-modules []
  (first (filter not-empty (map find-perl-module modules))))

(def updated-modules (find-updated-modules))

(defn mail-message [modules]
  (map (fn [m]
         (let [name (:name m)
               version (:version m)
               matcher (re-matcher #"_" version)
               dev (re-find matcher)]
           (str (format "%s-%s" name version) (if dev " DEVELEMENT VERSION\n" "")))) modules))

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
       emails))

(defn -main [& args]
  (send-all-module-email updated-modules)
  (let [update-seen (drop-while empty? (apply conj seen updated-modules))]
    (spit "seen.json" (json/json-str updated-modules))))
