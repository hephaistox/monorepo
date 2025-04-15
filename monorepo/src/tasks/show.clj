(ns tasks.show
  (:require [reference-deps :refer [check-deps]]
            [update-edn :refer [update-order]]))

(defn do-show
  [{:keys [title errorln normalln uri-str], :as printers}]
  (update-order printers "monorepo" "reference/deps.edn")
  (doseq [project (->> (check-deps "monorepo/reference/deps.edn")
                       vals
                       (sort-by :order))]
    (let [{:keys [prj git-status actual-branch bb deps]} project
          bb (vec (remove :matching? bb))
          deps (remove :matching? deps)]
      (title (str "Project " prj ", (branch " (uri-str actual-branch) ")"))
      (when (seq bb) (errorln "Errors in `bb.edn`"))
      (doseq [bb-item bb]
        (let [{:keys [actual path expect file]} bb-item]
          (normalln (uri-str file)
                    "at" (uri-str path)
                    "contains" (uri-str actual)
                    "and expect" (uri-str expect))))
      (when (seq deps) (errorln "Errors in `deps.edn`"))
      (doseq [deps-item deps]
        (let [{:keys [actual path expect file]} deps-item]
          (normalln (uri-str file)
                    "at" (uri-str path)
                    "contains" (uri-str actual)
                    "and expect" (uri-str expect))))
      (when (seq git-status)
        (errorln "Dirty state:")
        (normalln "Files under change are:" (uri-str git-status))))))
