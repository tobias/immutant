;; Copyright 2008-2013 Red Hat, Inc, and individual contributors.
;; 
;; This is free software; you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as
;; published by the Free Software Foundation; either version 2.1 of
;; the License, or (at your option) any later version.
;; 
;; This software is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
;; Lesser General Public License for more details.
;; 
;; You should have received a copy of the GNU Lesser General Public
;; License along with this software; if not, write to the Free
;; Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
;; 02110-1301 USA, or see the FSF site: http://www.fsf.org.

(ns ^{:no-doc true} immutant.runtime.bootstrap
  "Functions used in app bootstrapping. Should not be used in an app runtime."
  (:require [clojure.java.io          :as io]
            [clojure.walk             :as walk]
            [clojure.set              :as set]
            [clojure.tools.logging    :as log]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project   :as project])
  (:use immutant.runtime-util))

(defn ^{:internal true} read-project
  "Reads a leiningen project.clj file in the given root dir."
  [app-root profiles]
  (let [project-file (io/file app-root "project.clj")]
    (when (.exists project-file)
      (let [normalized-profiles (normalize-profiles profiles)
            project (project/read
                     (.getAbsolutePath project-file)
                     normalized-profiles)
            other-profiles (set (get-in project [:immutant :lein-profiles]))]
        (if (or (seq profiles) (not (seq other-profiles)))
          project
          (-> project
              (project/unmerge-profiles
               (set/difference normalized-profiles
                               other-profiles))
              (project/merge-profiles
               (set/difference other-profiles
                               normalized-profiles))))))))

(defn ^:private strip-reduce-metadata [v]
  (reduce (fn [acc k]
            (if (-> acc k meta :reduce)
              (update-in acc [k] vary-meta dissoc :reduce)
              acc))
          v
          (keys v)))

(defn ^{:internal true} read-project-to-string
  "Returns the project map as a pr string with metadata so it can be
  moved across runtimes."
  [app-root profiles]
  ;; reduce metadata points to a function, which won't serialize, so
  ;; we have to strip it out. pre-1.5, walk/postwalk worked, but it
  ;; now preserves the original metadata (which I consider a bug)
  (pr-str-with-meta
    (if-let [p (read-project app-root profiles)]
      (-> p
          strip-reduce-metadata
          (vary-meta #(update-in % [:without-profiles] strip-reduce-metadata))))))

(defn ^{:internal true} read-full-app-config
  "Returns the full configuration for an app. This consists of
the :immutant map from project.clj (if any) with the contents of the
internal descriptor map merged onto it (if any) followed by the
descriptor map (if any). Returns {} if none are available."
[descriptor-file app-root]
(let [external (read-descriptor descriptor-file)
      internal (read-descriptor (io/file app-root ".immutant.clj"))
      profiles (:lein-profiles external (:lein-profiles internal))]
    (merge {}
           (:immutant (read-project app-root profiles))
           internal
           external)))

(defn ^{:internal true} read-and-stringify-full-app-config
  "Loads the full app config and stringifies the keys."
  [descriptor-file app-root]
  (-> (read-full-app-config descriptor-file app-root)
      walk/stringify-keys
      stringify-init-symbol
      stringify-lein-profiles))

(defn ^{:internal true} read-full-app-config-to-string
  "Returns the full configuration for an app as a pr string that can
  move across runtimes."
  [descriptor-file app-root]
  (pr-str (read-full-app-config descriptor-file app-root)))

(defn ^{:private true :testable true} resolve-dependencies
  "Resolves dependencies from the lein project. It delegates to leiningen-core, but attempts
to gracefully handle missing dependencies."
  [project]
  (when project
    (project/load-certificates project)
    ;; trigger loading of s3-wagon-private, but don't load any other
    ;; plugins, since load-plugins tries to add them to the current
    ;; classloader, which will be the shared bootstrap classloader,
    ;; not the application's CL, and we don't want that to happen.
    (project/load-plugins project :no-plugins) 
    (try
      (->> (update-in project [:dependencies]
                      (fn [deps] (remove #(and
                                          (= "org.immutant" (namespace (first %)))
                                          (.startsWith (name (first %)) "immutant"))
                                        deps)))
           (classpath/resolve-dependencies :dependencies))
      (catch clojure.lang.ExceptionInfo e
        (log/error "The above resolution failure(s) prevented any maven dependency resolution. None of the dependencies listed in project.clj will be loaded from the local maven repository.")
        nil))))


(defn ^:internal resource-paths-for-project
  [project]
  ;; get the classpath w/o the deps, since we resolve those
  ;; elsewhere.
  (-> project
      (dissoc :dependencies)
      classpath/get-classpath))

(defn ^:internal resource-paths-for-project-string-as-string
  [project-as-string]
  (-> project-as-string
      read-string
      resource-paths-for-project
      pr-str-with-meta))

(defn ^{:internal true} resource-paths
  "Resolves the resource paths (in the AS7 usage of the term) for an application."
  [app-root profiles]
  (if-let [project (read-project app-root profiles)]
    (resource-paths-for-project project)
    (resource-paths-for-projectless-app app-root)))

(defn ^{:internal true} get-dependencies
  "Resolves the dependencies for an application. It concats bundled
jars with any aether resolved dependencies, with bundled jars taking
precendence. If resolve-deps is false, dependencies aren't resolved
via aether and only bundled jars are returned. This strips any
org.immutant/immutant-* deps from the list before resolving to prevent
conflicts with internal jars."
  ([app-root profiles resolve-deps?]
     (get-dependencies (or (read-project app-root profiles)
                           {:root app-root})
                       resolve-deps?))
  ([project resolve-deps?]
     (let [bundled (bundled-jars project)
           bundled-jar-names (map (fn [f] (.getName f)) bundled)]
       (concat
        bundled
        (when resolve-deps?
          (filter (fn [f] (not (some #{(.getName f)} bundled-jar-names)))
                  (resolve-dependencies project)))))))

(defn ^:internal get-dependencies-from-project-string-as-string
  [project-as-string resolve-deps?]
  (pr-str-with-meta
    (map #(.getAbsolutePath %)
         (get-dependencies (read-string project-as-string) resolve-deps?))))
