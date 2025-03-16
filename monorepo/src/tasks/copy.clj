(ns tasks.copy
  (:require [clojure.edn :as edn]
            [babashka.process :refer [shell]]
            [clojure.string :as str]))

(defn do-copy
  []
  (doseq [[_ m] (-> (slurp "monorepo/reference/copy_files.edn")
                    edn/read-string)]
    (let [{:keys [dirs files src]} m]
      (doseq [file files
              dir dirs]
        (let [cmd ["cp" (str src "/" file) (str dir "/" file)]]
          (println (str "Command `" (str/join " " cmd) "`"))
          (apply shell cmd))))))
