(ns lambdacd.util.internal.bash-test
  (:require [clojure.test :refer :all]
            [lambdacd.util.internal.bash :refer [bash]]
            [clojure.java.io :as io]
            [lambdacd.util.internal.temp :as temp-util]))

(deftest bash-util-test
  (testing "that it executes something on the bash"
    (let [cwd (temp-util/create-temp-dir)]
      (is (= {:out  "helloworld\n"
              :err  ""
              :exit 1} (bash cwd
                             "touch some-file"
                             "echo helloworld"
                             "exit 1")))
      (is (.exists (io/file cwd "some-file"))))))
