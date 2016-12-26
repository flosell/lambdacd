(ns lambdacd.util.internal.temp-test
  (:require [clojure.test :refer :all]
            [lambdacd.util.internal.temp :refer :all]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(deftest create-temp-dir-test
  (testing "creating in default tmp folder"
    (testing "that we can create a temp-directory"
      (is (fs/exists? (io/file (create-temp-dir)))))
    (testing "that it is writable"
      (is (fs/mkdir (io/file (create-temp-dir) "hello")))))
  (testing "creating in a defined parent directory"
    (testing "that it is a child of the parent directory"
      (let [parent (create-temp-dir)]
        (is (= parent (.getParent (io/file (create-temp-dir parent)))))))))

(defn- throw-if-not-exists [f]
  (if (not (fs/exists? f))
    (throw (IllegalStateException. (str f " does not exist")))
    "some-value-from-function"))

(deftest with-temp-test
  (testing "that a tempfile is deleted after use"
    (let [f (create-temp-file)]
      (is (= "some-value-from-function" (with-temp f (throw-if-not-exists f))))
      (is (not (fs/exists? f)))))
  (testing "that a tempfile is deleted when body throws"
    (let [f (create-temp-file)]
      (is (thrown? Exception (with-temp f (throw (Exception. "oh no!")))))
      (is (not (fs/exists? f)))))
  (testing "that a temp-dir is deleted after use"
    (let [d (create-temp-dir)]
      (fs/touch (fs/file d "somefile"))

      (is (= "some-value-from-function" (with-temp d (throw-if-not-exists d))))

      (is (not (fs/exists? (fs/file d "somefile"))))
      (is (not (fs/exists? d)))))
  (testing "that it can deal with circular symlinks"
    (let [f (create-temp-dir)]
      (is (= "some-value-from-function"
             (with-temp f (let [link-parent (io/file f "foo" "bar")]
                            (fs/mkdirs link-parent)
                            (fs/sym-link (io/file link-parent "link-to-the-start") f)
                            "some-value-from-function"
                            ))))
      (is (not (fs/exists? f))))))
