(ns tasks.status
  "Status of project dependencies"
  (:require [auto-build.project.cfg-mgt :refer
             [project-dir is-git-repo? extract-project git-data]]
            [auto-build.project.deps :as pd :refer
             [extract-paths-to-deps flatten-deps]]
            [auto-build.os.cmd :refer [printing]]
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
;; Status
;; ********************************************************************************

(defn run
  "Display the target application status of the monorepo, (as defined in the cli args)."
  [{:keys [title subtitle normalln errorln uri-str], :as printers}
   is-monorepo-app? cli-args]
  (let [target (-> cli-args
                   first
                   str)
        target-dir (project-dir target)]
    (title "Target project:" (uri-str target-dir))
    (if-not (is-git-repo? target-dir)
      (do (errorln "The target project is not a valid monorepo git repo")
          (print-repos printers))
      (loop [app-fullnames-to-check #{target}
             app-fullnames-done #{}
             deps-to-check []
             pass true]
        (let [[app-fullname & rapp-fullnames-to-check] app-fullnames-to-check
              [dep-to-check & rdeps-to-check] deps-to-check]
          (cond (seq dep-to-check)
                  (let [{:keys [path dep-alias dep-desc]} dep-to-check
                        {:git/keys [sha], :local/keys [root]} dep-desc
                        app-dir (-> dep-alias
                                    str
                                    extract-project
                                    project-dir)
                        {:keys [actual-sha]} (retrieve-local-git app-dir
                                                                 git-data)]
                    (if (= actual-sha sha)
                      (normalln (uri-str path)
                                "->"
                                (uri-str dep-alias)
                                (str "aligned with " (uri-str app-dir)))
                      (do (errorln (uri-str path)
                                   "->" (uri-str dep-alias)
                                   "should be" (uri-str actual-sha))
                          (if root
                            (normalln (apply str
                                        (repeat (+ 4 (count (str path))) " "))
                                      "is linked to local project: "
                                      (uri-str root))
                            (normalln (apply str
                                        (repeat (+ 4 (count (str path))) " "))
                                      "is"
                                      (uri-str sha)))))
                    (let [pass (and pass (= actual-sha sha))]
                      (if (get app-fullnames-done dep-alias)
                        (recur app-fullnames-to-check
                               app-fullnames-done
                               rdeps-to-check
                               pass)
                        (recur (conj app-fullnames-to-check app-dir)
                               app-fullnames-done
                               rdeps-to-check
                               pass))))
                (some? app-fullname)
                  (let [app-dir (-> app-fullname
                                    project-dir)
                        app-deps (-> (pd/read printers app-dir)
                                     :edn
                                     extract-paths-to-deps
                                     flatten-deps)
                        monorepo-deps (filter (comp is-monorepo-app? :dep-alias)
                                        app-deps)
                        {:keys [git-status actual-branch]}
                          (retrieve-local-git app-dir git-data)]
                    (subtitle "Scan application:"
                              (uri-str app-fullname)
                              (str "(dir " (uri-str app-dir) ")"))
                    (if (seq git-status)
                      (do (errorln "Branch"
                                   (uri-str actual-branch)
                                   (str "has pending modifications ("
                                        (uri-str app-dir)
                                        ")"))
                          (printing ["git" "status" "-s"]
                                    app-dir
                                    normalln
                                    errorln
                                    10))
                      (normalln "Branch" (uri-str actual-branch) "is uptodate"))
                    (recur (into #{} rapp-fullnames-to-check)
                           (conj app-fullnames-done app-fullname)
                           (concat deps-to-check monorepo-deps)
                           pass))
                :else
                  (if pass
                    (normalln "\nProject" (uri-str target) " deps are aligned")
                    (errorln "Project " (uri-str target) " ko"))))))))
