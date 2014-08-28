(defproject mesos-crate "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.palletops/clj-jclouds "0.1.1"]
                 [com.palletops/pallet "0.8.0-RC.9"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
