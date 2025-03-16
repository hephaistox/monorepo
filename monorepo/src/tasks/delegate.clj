(ns tasks.delegate
  (:require [auto-build.os.cmd :refer [printing]]
            [reference-deps]))

(defn do-delegate
  [{:keys [title normalln errorln]} cmd]
  (->> "monorepo/reference/deps.edn"
       reference-deps/read
       vals
       (map (fn [{:keys [dir prj]}]
              (title prj)
              (printing cmd dir normalln errorln 1000)))
       dorun))
