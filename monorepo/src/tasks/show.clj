(ns tasks.show
  (:require [reference-deps :refer [check-deps]]))

(defn do-show
  [{:keys [title errorln normalln uri-str]}]
  (doseq [project (->> (check-deps "monorepo/reference/deps.edn")
                       vals
                       (sort-by :order))]
    (let [{:keys [prj git-status actual-branch bb deps]} project
          bb (vec (remove :matching? bb))
          deps (remove :matching? deps)]
      (title "Project" prj)
      (normalln "Branch" (uri-str actual-branch))
      (when (seq bb) (errorln "Errors in `bb.edn`"))
      (doseq [bb-item bb]
        (let [{:keys [actual path expect file]} bb-item]
          (normalln (uri-str file) "at" (uri-str path))
          (normalln "contains" (uri-str actual) "and expect" (uri-str expect))))
      (when (seq deps) (errorln "Errors in `deps.edn`"))
      (doseq [deps-item deps]
        (let [{:keys [actual path expect file]} deps-item]
          (normalln (uri-str file) "at" (uri-str path))
          (normalln "contains" (uri-str actual) "and expect" (uri-str expect))))
      (when (seq git-status)
        (errorln "Dirty state")
        (normalln "Files under change are:" (uri-str git-status))))))
