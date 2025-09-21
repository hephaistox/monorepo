(ns tasks.latest
  "Move ref to latest"
  (:require [auto-build.project.cfg-mgt :refer
             [project-dir is-git-repo? extract-project git-data]]
            [auto-build.project.deps :as pd :refer
             [extract-paths-to-deps flatten-deps update-deps-edn]]
            [tasks.projects :refer [print-repos]]))

(def local-deps-repo (atom {}))

(defn retrieve-local-git
  [prj-dir retriever]
  (if-let [prj-deps (get @local-deps-repo prj-dir)]
    prj-deps
    (when-let [prj-data (retriever prj-dir)]
      (swap! local-deps-repo assoc prj-dir prj-data)
      prj-data)))

;; ********************************************************************************
;; Latest
;; ********************************************************************************

(defn run
  "Move local references to latest git"
  [{:keys [title normalln errorln uri-str], :as printers} is-monorepo-app?
   cli-args]
  (let [target (-> cli-args
                   first
                   str)
        target-dir (project-dir target)]
    (if-not (is-git-repo? target-dir)
      (do (errorln "The target project"
                   (uri-str target-dir)
                   " s not a valid monorepo git repo")
          (print-repos printers))
      (do
        (normalln "Target project:" (uri-str target-dir))
        (loop [app-fullnames-to-check #{target}
               app-fullnames-done #{}
               deps-to-check []]
          (let [[app-fullname & rapp-fullnames-to-check] app-fullnames-to-check
                [dep-to-check & rdeps-to-check] deps-to-check]
            (cond (seq dep-to-check)
                    (let [{:keys [path dep-alias dep-desc deps-dir]}
                            dep-to-check
                          {:git/keys [sha]} dep-desc
                          app-dir (-> dep-alias
                                      str
                                      extract-project
                                      project-dir)
                          {:keys [actual-sha]} (retrieve-local-git app-dir
                                                                   git-data)]
                      (if (= sha actual-sha)
                        (normalln (uri-str path) "is up-to-date")
                        (do (normalln (uri-str path)
                                      "is moved to sha"
                                      (uri-str actual-sha))
                            (update-deps-edn printers
                                             deps-dir
                                             (concat path [dep-alias])
                                             {:git/sha actual-sha})))
                      (if (get app-fullnames-done dep-alias)
                        (recur app-fullnames-to-check
                               app-fullnames-done
                               rdeps-to-check)
                        (recur (conj app-fullnames-to-check app-dir)
                               app-fullnames-done
                               rdeps-to-check)))
                  (some? app-fullname)
                    (let [app-dir (-> app-fullname
                                      project-dir)
                          app-deps (map #(assoc % :deps-dir app-dir)
                                     (-> (pd/read printers app-dir)
                                         :edn
                                         extract-paths-to-deps
                                         flatten-deps))
                          monorepo-deps (filter (comp is-monorepo-app?
                                                      :dep-alias)
                                          app-deps)]
                      (title "Scan application:"
                             (uri-str app-fullname)
                             (str "(dir " (uri-str app-dir) ")"))
                      (recur (into #{} rapp-fullnames-to-check)
                             (conj app-fullnames-done app-fullname)
                             (concat deps-to-check monorepo-deps)))
                  :else (normalln "\nEnd of local alignment"))))))))
