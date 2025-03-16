(ns tasks.push
  (:require [reference-deps :refer [check-deps]]
            filepath
            update-edn))

(comment
  (check-deps "monorepo/reference/deps.edn")
  ;
)

(defn do-push
  [{:keys [title normalln uri-str], :as printers} args]
  (let [project-to-update (keyword (first args))
        local-root? (some? (second args))]
    (doseq [project (->> (check-deps "monorepo/reference/deps.edn")
                         vals
                         (sort-by :order))]
      (let [{:keys [prj bb deps]} project
            project-dir (:dir project)]
        (title "Project" prj)
        (doseq [bb-item bb]
          (let [{:keys [path expect file src-prj src-prj-dir]} bb-item]
            (when (= src-prj project-to-update)
              (let [ref (if local-root?
                          {:local/root (filepath/relativize src-prj-dir
                                                            project-dir)}
                          {:git/sha expect})]
                (update-edn/assoc-in printers project-dir file path ref)
                (normalln "Has updated" (uri-str file) "with" (uri-str ref))))))
        (doseq [deps-item deps]
          (let [{:keys [path expect src-prj file src-prj-dir]} deps-item]
            (when (= src-prj project-to-update)
              (let [ref (if local-root?
                          {:local/root (filepath/relativize src-prj-dir
                                                            project-dir)}
                          {:git/sha expect})]
                (update-edn/assoc-in printers project-dir file path ref)
                (normalln "Has updated" (uri-str file)
                          "with" (uri-str ref))))))))))
