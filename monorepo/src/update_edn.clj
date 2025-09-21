(ns update-edn
  (:refer-clojure :exclude [assoc-in])
  (:require [clojure.edn :as edn]
            [auto-build.code.formatter :refer [format-file]]))

(defn assoc-in
  "Associates value `val` to the nested associative structure where `ks` is a sequence of keys."
  [printers project-dir path ks val]
  (let [fullpath (str project-dir "/" path)
        new-content (some-> fullpath
                            slurp
                            edn/read-string
                            (clojure.core/assoc-in ks val))]
    (spit fullpath new-content)
    (format-file printers project-dir path)))

(defn update-order
  [printers project-dir path]
  (let [fullpath (str project-dir "/" path)
        content (some-> fullpath
                        slurp
                        edn/read-string)
        project-dir (str project-dir)
        path (str path)
        new-content (->> content
                         (sort-by (comp :order second))
                         vec)]
    (spit fullpath new-content)
    (format-file printers project-dir path)))
