(defproject clj-twitter-feelings "1.0.0"
  :description "How is Twitter feeling now?"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.apache.httpcomponents/httpclient "4.0.1"]
                 [net.sf.squirrel-sql.thirdparty-non-maven/substance "5.2_01"]
                 [com.miglayout/miglayout "3.7.3"]
                 [jfree/jfreechart "1.0.12"]]
  :dev-dependencies [[leiningen-run "0.2"]]
  :main clj-twitter-feelings.ui
  :aot [clj-twitter-feelings.core]
  :warn-on-reflection true
  :jar-dir "target")