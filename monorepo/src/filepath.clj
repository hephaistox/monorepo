(ns filepath
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def directory-separator
  "Symbol to separate directories.
  Is usually `/` on linux based OS And `\\` on windows based ones"
  fs/file-separator)

(defn remove-trailing-separator
  "If exists, remove the trailing separator in a path, remove unwanted spaces either"
  [path]
  (let [path (str/trim path)]
    (if (= (str directory-separator) (str (last path)))
      (->> (dec (count path))
           (subs path 0)
           remove-trailing-separator)
      path)))

(defn absolutize
  "Returns the absolute path of `relative-path` (file or dir)."
  [relative-path]
  (let [relative-path (if (nil? relative-path) "" relative-path)]
    (str (fs/absolutize relative-path))))

(defn relativize
  "Turn the `path` into a relative directory starting from `root-dir`"
  [path root-dir]
  (let [path (-> path
                 remove-trailing-separator
                 absolutize)
        root-dir (-> root-dir
                     remove-trailing-separator
                     absolutize)]
    (when-not (str/blank? root-dir)
      (->> path
           (fs/relativize root-dir)
           str))))
