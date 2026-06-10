(ns tasks.projects
  "Display local repositories"
  (:require [clojure.string :as str]
            [auto-build.project.cfg-mgt :refer [local-repos]]))

(defn print-repos
  [{:keys [normalln title], :as _printers}]
  (title "Choose one among:")
  (normalln (str/join ", " (local-repos "."))))
