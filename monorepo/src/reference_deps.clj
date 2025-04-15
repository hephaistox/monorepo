(ns reference-deps
  (:refer-clojure :exclude [read])
  (:require [clojure.edn :as edn]
            [auto-build.os.cmd :as build-cmd :refer [as-string]]
            [clojure.string :as str]))

(defn- to-dir [dir prj] (or dir (str/replace (name prj) (re-pattern "-") "_")))

(defn read
  [filename]
  (->> (slurp filename)
       edn/read-string
       (mapv (fn [{:keys [dir app-id], :as d}] [app-id
                                                (assoc d
                                                  :prj app-id
                                                  :dir (to-dir dir app-id))]))
       (into (sorted-map))))

(defn actual-git
  "Add git data"
  [filename]
  (-> (read filename)
      (update-vals (fn [{:keys [dir], :as project}]
                     (assoc project
                       :git-status (-> ["git" "status" "-s"]
                                       (as-string dir)
                                       :out-stream)
                       :actual-branch (-> ["git" "rev-parse" "--abbrev-ref"
                                           "HEAD"]
                                          (as-string dir)
                                          :out-stream
                                          first)
                       :actual-sha (-> ["git" "rev-parse" "HEAD"]
                                       (as-string dir)
                                       :out-stream
                                       first))))))

(comment
  (read "monorepo/reference/deps.edn")
  (actual-git "monorepo/reference/deps.edn")
  ;
)

(defn check-deps
  [filename]
  (let [ref-deps (actual-git filename)]
    (update-vals
      ref-deps
      (fn [project]
        (let [deps-file (some-> (:dir project)
                                (str "/deps.edn"))
              deps-content! (delay (some-> deps-file
                                           slurp
                                           edn/read-string))
              bb-file (some-> (:dir project)
                              (str "/bb.edn"))
              bb-content! (delay (some-> bb-file
                                         slurp
                                         edn/read-string))]
          (-> project
              (update
                :deps
                (fn [deps-items]
                  (->> deps-items
                       (mapv
                         (fn [{:keys [path src-prj], :as deps-item}]
                           (let [expect (get-in ref-deps [src-prj :actual-sha])
                                 actual (:git/sha (get-in @deps-content! path))]
                             (assoc deps-item
                               :actual actual
                               :expect expect
                               :project-dir (:dir project)
                               :src-prj-dir (get-in ref-deps [src-prj :dir])
                               :file "deps.edn"
                               :matching? (= actual expect))))))))
              (update
                :bb
                (fn [bb-items]
                  (->> bb-items
                       (mapv
                         (fn [{:keys [path src-prj], :as bb-item}]
                           (let [expect (get-in ref-deps [src-prj :actual-sha])
                                 actual (:git/sha (get-in @bb-content! path))]
                             (assoc bb-item
                               :actual actual
                               :expect expect
                               :project-dir (:dir project)
                               :src-prj-dir (get-in ref-deps [src-prj :dir])
                               :file "bb.edn"
                               :matching? (= actual expect))))))))))))))
