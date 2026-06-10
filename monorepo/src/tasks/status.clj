(ns tasks.status
  "Status of project monorepo, dependencies of project."
  (:require [auto-build.project.cfg-mgt :refer
             [project-dir is-git-repo? git-subdir-data]]
            [auto-build.project.deps :as pd :refer
             [extract-paths-to-deps flatten-deps]]
            [auto-build.os.cmd :refer [printing]]
            [clojure.set]
            [tasks.projects :refer [print-repos]]))

;; ********************************************************************************
;; Read git data from a local repo, and some
;; ********************************************************************************

(def ^:private local-deps-repo
  "Cache of local repos used to limit git usage"
  (atom {}))

(defn- retrieve-local-git
  "Use `retriever` in project-dir if cache is not present."
  [prj-dir retriever]
  (if-let [prj-deps (get @local-deps-repo prj-dir)]
    prj-deps
    (when-let [prj-data (retriever prj-dir)]
      (swap! local-deps-repo assoc prj-dir prj-data)
      prj-data)))

(comment
  (git-subdir-data "../auto_web/auto_web_cljc")
  (retrieve-local-git "../auto_web/auto_web_cljc" git-subdir-data)
  ;;
)

(defn- current-branch-commited
  "Returns git status of the repo in `local-app-dir`.
  - `true` there is no modification pending.
  - `false` a modification is pending."
  [local-git]
  (= nil
     (-> local-git
         :git-status
         seq)))

(comment
  (-> (retrieve-local-git "../auto_web/auto_web_cljc" git-subdir-data)
      current-branch-commited)
  ;
)

(defn- print-pending-modifications
  [local-app-dir printers]
  (let [{:keys [normalln errorln]} printers]
    (printing ["git" "status" "-s"] local-app-dir normalln errorln 10)))

(comment
  (print-pending-modifications "../auto_core"
                               {:normalln println, :errorln println})
  ;;
)

;; ********************************************************************************
;; Monorepo dependency
;; ********************************************************************************

(defn- monorepo-deps
  "Returns monorepo dependencies
  
  Read in the `deps.edn` file in directory `local-app-dir`. All dependencies are gathered, and the one matching is-monorepo-app? are returned."
  [local-app-dir printers]
  (-> (pd/read printers local-app-dir)
      :edn
      extract-paths-to-deps
      (flatten-deps local-app-dir)))

(comment
  (monorepo-deps "../landing" {:normalln println, :errorln println})
  ;;
)

;; ********************************************************************************
;; External dependency
;; ********************************************************************************

(defn- local-deps-sha
  "Returns the commit sha of application stored in `local-app-dir`."
  [local-app-dir]
  (-> local-app-dir
      (retrieve-local-git git-subdir-data)
      :actual-sha))

(comment
  (local-deps-sha "../auto_web/auto_web_cljc")
  ;
)

;; ********************************************************************************
;; External dependency
;; ********************************************************************************

(defn- app-deps-updtodate?
  "Are dependencies of `local-git` aligned with local commit?"
  [{:keys [dir dep dep-alias path]}]
  (when-not (= (:git/sha dep) (local-deps-sha dir))
    {:dep-alias dep-alias,
     :path path,
     :request (:git/sha dep),
     :actual (local-deps-sha dir)}))

(comment
  (->> (monorepo-deps "../auto_web/auto_web_cljc"
                      {:normalln println, :errorln println})
       first
       app-deps-updtodate?)
  ;;
)

(defn- scan-local-applications
  "For a local application stored in `target-dir`, scan all dependencies matching `is-monorepo-app?`."
  [{:keys [subtitle errorln normalln], :as printers} target-dir]
  (loop [[local-app-dir & rlocal-app-dirs] #{target-dir}
         local-app-dir-done #{}
         task-pass true]
    (if (some? local-app-dir)
      (let [{local-deps true, git-deps false} (->> (monorepo-deps local-app-dir
                                                                  printers)
                                                   (group-by :is-local?))
            local-app-git (retrieve-local-git local-app-dir git-subdir-data)
            local-app-commited? (current-branch-commited local-app-git)
            non-aligned-deps (keep app-deps-updtodate? git-deps)]
        (if local-app-commited?
          (-> (format "Scan app in directory `%s`, branch `%s` is clean"
                      local-app-dir
                      (:actual-branch local-app-git))
              subtitle)
          (do (-> (format "Scan app in directory `%s`, branch `%s`"
                          local-app-dir
                          (:actual-branch local-app-git))
                  subtitle)
              (errorln "Pending modifications are:")
              (print-pending-modifications local-app-dir printers)))
        (doseq [{:keys [request actual path dep-alias]} non-aligned-deps]
          (-> (format "`%s`->`%s`" dep-alias path)
              errorln)
          (-> (format "   requests `%s`, actual is `%s`" request actual)
              normalln))
        (doseq [local-dep local-deps]
          (errorln (format "`%s` is a local dependency"
                           (:dep-alias local-dep))))
        (recur (clojure.set/difference (into #{}
                                             (concat rlocal-app-dirs
                                                     (mapv :dir git-deps)
                                                     (mapv :dir local-deps)))
                                       local-app-dir-done)
               (conj local-app-dir-done local-app-dir)
               (and task-pass
                    (empty? local-deps)
                    (empty? non-aligned-deps)
                    local-app-commited?)))
      task-pass)))

;; ********************************************************************************
;; Status task
;; ********************************************************************************

(defn run
  "Displays the status of a target application in the monorepo.

  * The `target` application is the name of the directory, defined as the first cli arg.
  * All `monorepo-app` is stored locally in this directory."
  [{:keys [title errorln uri-str normalln], :as printers} cli-args]
  (let [target (-> cli-args
                   first
                   str)
        target-dir (project-dir target)]
    (title "Target project:" (uri-str target-dir))
    (if-not (is-git-repo? target-dir)
      (do (errorln "The target project is not a valid monorepo git repo")
          (print-repos printers))
      (if (scan-local-applications printers target)
        (do (title "Synthesis:")
            (normalln "Monorepo is valid for target " (uri-str target-dir)))
        (errorln "Problems found in the monorepo")))))
