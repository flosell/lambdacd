(ns lambdacd.util-test
  (:use [lambdacd.util])
  (:require [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [conjure.core :as c]))

(deftest range-test
  (testing "that range produces a range from a value+1 with a defined length"
    ; TODO: the plus-one is like that because the user wants it, probably shouldn't be like this..
    (is (= '(6 7 8) (range-from 5 3)))))

(defn some-function [] {})


(deftest map-if-test
  (testing "that is applies a function to all elements that match a predicate"
    (is (= []      (map-if (identity true) inc [])))
    (is (= [4 3 5] (map-if #(< % 5) inc [3 2 4])))
    (is (= [3 2 5] (map-if #(= 4 %) inc [3 2 4])))))

(deftest put-if-not-present-test
  (testing "that it adds a value to a map only of no value exists for this key"
    (is (= {:foo :bar}       (put-if-not-present {:foo :bar} :foo :baz)))
    (is (= {:foo :baz}       (put-if-not-present {} :foo :baz)))
    (is (= {:a :b :foo :baz} (put-if-not-present {:a :b} :foo :baz)))))

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


(deftest json-test
  (testing "that a proper ring-json-response is returned"
    (is (= {:body    "{\"hello\":\"world\"}"
            :headers {"Content-Type" "application/json;charset=UTF-8"}
            :status  200} (json { :hello :world })))))

(deftest parse-int-test
  (testing "that we can parse integers"
    (is (= 42 (parse-int "42")))
    (is (= -1 (parse-int "-1")))
    (is (thrown? NumberFormatException (parse-int "foo")))))

(deftest fill-test
  (testing "that we can fill up a sequence to a certain length"
    (is (= [1 2 3 -1 -1] (fill [1 2 3] 5 -1))))
  (testing "that a collection is left just as it was if it is already longer than the desired length"
    (is (= [1 2 3] (fill [1 2 3] 2 -1)))
    (is (= [1 2 3] (fill [1 2 3] 3 -1)))))

(defn f [k v1 v2]
  (str k v1 v2))

(deftest merge-with-k-v-test
  (testing "that conflicts get passed to the passed function"
      (c/mocking [f]
                 (merge-with-k-v f {:foo 1} {:foo 2})
                 (c/verify-first-call-args-for f :foo 1 2)))
  (testing "a merge"
    (is (= {:foo ":foo12" :a 1 :b 2} (merge-with-k-v f {:foo 1 :a 1} {:foo 2 :b 2})))))
