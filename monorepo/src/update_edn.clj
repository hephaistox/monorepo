(ns update-edn
  (:refer-clojure :exclude [assoc-in])
  (:require [clojure.edn :as edn]
            [auto-build.code.formatter :refer [format-file]]))

(defn assoc-in
  [printers project-dir path ks val]
  (let [fullpath (str project-dir "/" path)
        new-content (some-> fullpath
                            slurp
                            edn/read-string
                            (clojure.core/assoc-in ks val))]
    (spit fullpath new-content)
    (format-file printers project-dir path)))
