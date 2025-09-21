(ns tasks.common)

(def local-deps-repo (atom {}))

(defn retrieve-local-git
  [prj-dir retriever]
  (if-let [prj-deps (get @local-deps-repo prj-dir)]
    prj-deps
    (when-let [prj-data (retriever prj-dir)]
      (swap! local-deps-repo assoc prj-dir prj-data)
      prj-data)))

(comment
  (retrieve-local-git "landing" git-data)
  ;;
)
