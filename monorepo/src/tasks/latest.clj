(ns tasks.latest
  "Move ref to latest"
  (:require [auto-build.project.cfg-mgt :refer
             [project-dir extract-project is-git-repo? git-subdir-data]]
            [clojure.set]
            [auto-build.project.deps :as pd :refer
             [extract-paths-to-deps flatten-deps update-deps-edn]]
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


;; ********************************************************************************
;; Latest
;; ********************************************************************************

(defn- bb-deps-to-latest
  "Re-pin the hephaistox tooling dependencies (e.g. `auto-build`) declared in the
  `bb.edn` of `local-app-dir` to the latest commit of their local sibling.

  This is needed because `bb.edn` is not part of the `deps.edn` dependency graph."
  [{:keys [normalln], :as printers} local-app-dir]
  (doseq [{:keys [dep-alias dep path]} (pd/bb-hephaistox-deps printers
                                                              local-app-dir)]
    (let [{:git/keys [sha]} dep
          build-dir (-> dep-alias
                        str
                        extract-project
                        project-dir)
          {:keys [actual-sha]} (retrieve-local-git build-dir git-subdir-data)]
      (when actual-sha
        (if (= sha actual-sha)
          (normalln (format "`%s` (bb.edn) is already uptodate sha `%s`"
                            dep-alias
                            actual-sha))
          (do (normalln (format "`%s` (bb.edn) is moved to sha `%s`"
                                dep-alias
                                actual-sha))
              (pd/update-bb-edn printers
                                local-app-dir
                                path
                                (-> dep
                                    (dissoc :local/root)
                                    (assoc :git/sha actual-sha)))))))))

(defn- move-to-latest
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
        (doseq [local-dep local-deps]
          (let [{:keys [dep-alias dep path dir]} local-dep
                {:hephaistox/keys [url root]} dep
                {:keys [actual-sha]} (retrieve-local-git dir git-subdir-data)]
            (normalln (format "`%s` is moved from local version to sha `%s`"
                              dep-alias
                              actual-sha))
            (update-deps-edn printers
                             local-app-dir
                             path
                             (-> dep
                                 (dissoc :local/root)
                                 (assoc :git/sha actual-sha
                                        :git/url url)
                                 (cond-> root (assoc :deps/root root))))))
        (doseq [git-dep git-deps]
          (let [{:keys [dep dep-alias path dir]} git-dep
                {:git/keys [sha], :hephaistox/keys [url root]} dep
                {:keys [actual-sha]} (retrieve-local-git dir git-subdir-data)]
            (if (= sha actual-sha)
              (normalln (format "`%s` is already uptodate sha `%s`"
                                dep-alias
                                actual-sha))
              (do (normalln (format "`%s` is moved from sha `%s` to sha `%s`"
                                    dep-alias
                                    sha
                                    actual-sha))
                  (update-deps-edn printers
                                   local-app-dir
                                   path
                                   (-> dep
                                       (dissoc :local/root)
                                       (assoc :git/sha actual-sha
                                              :git/url url)
                                       (cond-> root (assoc :deps/root
                                                      root))))))))
        (bb-deps-to-latest printers local-app-dir)
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
      (move-to-latest printers target-dir))))
