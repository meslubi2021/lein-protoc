(ns leiningen.protoc-test
  (:require [leiningen.protoc :as lp]
            [leiningen.core.project :as lcp]
            [leiningen.core.utils :as lcu]
            [clojure.java.io :as io]
            [clojure.test :as t]))

(def basic-project
  (lcp/read "test/test-basic-project.clj"))

(def basic-proto-file
  (io/file "test/proto/basic/Foo.proto"))

(def basic-java-file
  (io/file "test/target/generated-sources/protobuf/com/liaison/foo/FooProtos.java"))

(def grpc-project
  (lcp/read "test/test-grpc-project.clj"))

(def grpc-java-file-1
  (io/file "test/target/generated-sources/protobuf/com/liaison/bar/BarProtos.java"))

(def grpc-java-file-2
  (io/file "test/target/generated-sources/protobuf/com/liaison/bar/BarServiceGrpc.java"))

(t/use-fixtures
  :each
  (fn [f]
    (.delete basic-java-file)
    (.delete grpc-java-file-1)
    (.delete grpc-java-file-2)
    (f)))

(t/deftest basic-protoc-test
  (t/testing "Basic proto compilation"
    (let [result (lp/protoc basic-project)
          first-timestamp (.lastModified basic-java-file)]
      (t/is (.exists basic-proto-file))
      (t/is (.exists basic-java-file))
      ;; lastModified time only has resolution down to the second, not ms
      (Thread/sleep 1000)
      (lp/protoc basic-project)
      (let [second-timestamp (.lastModified basic-java-file)]
        (t/is (.exists basic-java-file))
        (t/is (= first-timestamp second-timestamp))
        (Thread/sleep 1000)
        (.setLastModified basic-proto-file (System/currentTimeMillis))
        (lp/protoc basic-project)
        (t/is (.exists basic-java-file))
        (t/is (not= first-timestamp (.lastModified basic-java-file)))))))

(t/deftest grpc-protoc-test
  (t/testing "gRPC proto compilation"
    (lp/protoc grpc-project)
    (t/is (.exists grpc-java-file-1))
    (t/is (.exists grpc-java-file-2))))

(t/deftest get-jar-fs-test
  (t/testing "Can open the same JAR twice"
    (let [path (lp/jar-uri "dev-resources/empty.jar")]
      (with-open [proto-jar-fs ^java.nio.file.FileSystem (lp/get-jar-fs path)]
        (with-open [second-proto-jar-fs ^java.nio.file.FileSystem (lp/get-jar-fs path)]
          (t/is (= proto-jar-fs second-proto-jar-fs)))))))

(t/deftest resolve-classifier-test
  (t/are [version os arch classifier]
         (= classifier (lp/resolve-classifier os arch version))

    "3.0.0-beta-2" "windows" "x86_32"   "windows-x86_32"
    "3.0.0-beta-2" "windows" "x86_64"   "windows-x86_64"
    "3.0.0-beta-2" "linux"   "x86_32"   "linux-x86_32"
    "3.0.0-beta-2" "linux"   "x86_64"   "linux-x86_64"
    "3.0.0-beta-2" "osx"     "x86_64"   "osx-x86_64"
    "3.0.0-beta-2" "osx"     "aarch_64" "osx-x86_64"     ; Redirected to x86_64
    "3.3.0"        "windows" "x86_32"   "windows-x86_32"
    "3.3.0"        "windows" "x86_64"   "windows-x86_64"
    "3.3.0"        "linux"   "x86_32"   "linux-x86_32"
    "3.3.0"        "linux"   "x86_64"   "linux-x86_64"
    "3.3.0"        "osx"     "x86_64"   "osx-x86_64"
    "3.3.0"        "osx"     "aarch_64" "osx-x86_64"     ; Redirected to x86_64
    "3.17.2"       "windows" "x86_32"   "windows-x86_32"
    "3.17.2"       "windows" "x86_64"   "windows-x86_64"
    "3.17.2"       "linux"   "x86_32"   "linux-x86_32"
    "3.17.2"       "linux"   "x86_64"   "linux-x86_64"
    "3.17.2"       "osx"     "x86_64"   "osx-x86_64"
    "3.17.2"       "osx"     "aarch_64" "osx-x86_64"     ; Redirected to x86_64
    "3.17.3"       "windows" "x86_32"   "windows-x86_32"
    "3.17.3"       "windows" "x86_64"   "windows-x86_64"
    "3.17.3"       "linux"   "x86_32"   "linux-x86_32"
    "3.17.3"       "linux"   "x86_64"   "linux-x86_64"
    "3.17.3"       "osx"     "x86_64"   "osx-x86_64"
    "3.17.3"       "osx"     "aarch_64" "osx-aarch_64"   ; Uses native aarch_64
    "3.18.0"       "windows" "x86_32"   "windows-x86_32"
    "3.18.0"       "windows" "x86_64"   "windows-x86_64"
    "3.18.0"       "linux"   "x86_32"   "linux-x86_32"
    "3.18.0"       "linux"   "x86_64"   "linux-x86_64"
    "3.18.0"       "osx"     "x86_64"   "osx-x86_64"
    "3.18.0"       "osx"     "aarch_64" "osx-aarch_64"   ; Uses native aarch_64
    "4.0.0-rc-1"   "windows" "x86_32"   "windows-x86_32"
    "4.0.0-rc-1"   "windows" "x86_64"   "windows-x86_64"
    "4.0.0-rc-1"   "linux"   "x86_32"   "linux-x86_32"
    "4.0.0-rc-1"   "linux"   "x86_64"   "linux-x86_64"
    "4.0.0-rc-1"   "osx"     "x86_64"   "osx-x86_64"
    "4.0.0-rc-1"   "osx"     "aarch_64" "osx-aarch_64"   ; Uses native aarch_64
    ))

(t/deftest resolve-protoc!-works-with-intel-and-arm-macs
  (with-redefs [lcu/get-os (constantly :macosx)]
    (t/testing "Old version on an Intel Mac"
      (with-redefs [lcu/get-arch (constantly :x86_64)]
        (t/is (= "protoc-3.6.0-osx-x86_64.exe"
                 (.getName (io/file (lp/resolve-protoc! "3.6.0")))))))
    (t/testing "Old version on an M1 Mac"
      (with-redefs [lcu/get-arch (constantly :aarch_64)]
        (t/is (= "protoc-3.6.0-osx-x86_64.exe"
                 (.getName (io/file (lp/resolve-protoc! "3.6.0")))))))
    (t/testing "New version on an Intel Mac"
      (with-redefs [lcu/get-arch (constantly :x86_64)]
        (t/is (= "protoc-3.20.0-osx-x86_64.exe"
                 (.getName (io/file (lp/resolve-protoc! "3.20.0")))))))
    (t/testing "New version on an M1 Mac"
      (with-redefs [lcu/get-arch (constantly :aarch_64)]
        (t/is (= "protoc-3.20.0-osx-aarch_64.exe"
                 (.getName (io/file (lp/resolve-protoc! "3.20.0")))))))))
