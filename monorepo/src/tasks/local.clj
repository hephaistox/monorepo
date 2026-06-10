(ns tasks.local
  "Move monorepo ref to local"
  (:require [auto-build.project.cfg-mgt :refer
             [project-dir extract-project is-git-repo? git-subdir-data]]
            [auto-build.project.deps :as pd :refer
             [extract-paths-to-deps flatten-deps update-deps-edn]]
            [babashka.fs :as fs]
            [clojure.set]
            [tasks.projects :refer [print-repos]]))

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

(defn- monorepo-deps
  "Returns monorepo dependencies
  
  Read in the `deps.edn` file in directory `local-app-dir`. All dependencies are gathered, and the one matching is-monorepo-app? are returned."
  [local-app-dir printers]
  (-> (pd/read printers local-app-dir)
      :edn
      extract-paths-to-deps
      (flatten-deps local-app-dir)))

(defn- bb-deps-to-local
  "Move the hephaistox tooling dependencies (e.g. `auto-build`) declared in the
  `bb.edn` of `local-app-dir` to their local sibling directory.

  This is needed because `bb.edn` is not part of the `deps.edn` dependency graph."
  [{:keys [normalln], :as printers} local-app-dir]
  (doseq [{:keys [dep-alias dep path is-local?]} (pd/bb-hephaistox-deps printers
                                                                       local-app-dir)]
    (let [build-dir (-> dep-alias
                        str
                        extract-project
                        project-dir)
          local-root (str (fs/relativize local-app-dir build-dir))]
      (if is-local?
        (normalln (format "`%s` (bb.edn) is already targeting local version"
                          dep-alias))
        (do (normalln (format "`%s` (bb.edn) is moved to local root `%s`"
                              dep-alias
                              local-root))
            (pd/update-bb-edn printers
                              local-app-dir
                              path
                              (-> dep
                                  (assoc :local/root local-root)
                                  (dissoc :git/sha :git/url))))))))

;; ********************************************************************************
;; Latest
;; ********************************************************************************

(defn- move-to-local
  [{:keys [normalln uri-str subtitle], :as printers} target-dir]
  (normalln "Target project:" (uri-str target-dir))
  (loop [[local-app-dir & rlocal-app-dirs] #{target-dir}
         local-app-dir-done #{}]
    (when local-app-dir
      (let [local-app-git (retrieve-local-git local-app-dir git-subdir-data)
            {local-deps true, git-deps false} (->> (monorepo-deps local-app-dir
                                                                  printers)
                                                   (group-by :is-local?))]
        (-> (format "Scan app in directory `%s`, branch `%s`"
                    local-app-dir
                    (:actual-branch local-app-git))
            subtitle)
        (doseq [{:keys [dep-alias]} local-deps]
          (normalln (format "`%s` is already targeting local version"
                            dep-alias)))
        (doseq [git-dep git-deps]
          (let [{:keys [dep path]} git-dep
                {:hephaistox/keys [dir root]} dep]
            (update-deps-edn printers
                             local-app-dir
                             path
                             (-> dep
                                 (assoc :local/root
                                          (str dir (when root (str "/" root))))
                                 (dissoc :git/sha :git/url)))))
        (bb-deps-to-local printers local-app-dir)
        (recur (clojure.set/difference (into #{}
                                             (concat rlocal-app-dirs
                                                     (mapv :dir git-deps)
                                                     (mapv :dir local-deps)))
                                       local-app-dir-done)
               (conj local-app-dir-done local-app-dir))))))

(defn run
  "Move local references to latest git"
  [{:keys [errorln uri-str], :as printers} cli-args]
  (let [target (-> cli-args
                   first
                   str)
        target-dir (project-dir target)]
    (if-not (is-git-repo? target-dir)
      (do (errorln "The target project"
                   (uri-str target-dir)
                   " s not a valid monorepo git repo")
          (print-repos printers))
      (move-to-local printers target-dir))))
