(ns lambdaci.visual-test
  (:use [lambdaci.dsl])
  (:require [clojure.test :refer :all]
            [lambdaci.visual :refer :all]))

(defn do-stuff [] {})
(defn do-other-stuff [] {})
(defn do-more-stuff [] {})
(defn do-even-more-stuff [] {})


(def pipeline
  `(
    (in-parallel
      (in-cwd "some-path" ;; I can't checkout yet so this will to to set up a working dir
        do-stuff)
      (in-cwd "some-other-path"
        do-other-stuff
        do-more-stuff))
    (in-cwd "some-path"
      do-even-more-stuff)
  ))

(deftest display-type-test
  (testing "that in-parallel gets detected"
    (is (= :parallel (display-type `in-parallel)))
    (is (= :parallel (display-type (first (first pipeline)))))
  )
  (testing "that other-steps are just steps"
    (is (= :step (display-type `in-cwd)))
    (is (= :step (display-type (first (second pipeline)))))
  )
)

(deftest display-name-test
  (testing "that the display-name for a step is just the function-name"
    (is (= "do-even-more-stuff" (display-name `do-even-more-stuff)))
    (is (= "do-even-more-stuff" (display-name (last (second pipeline)))))
  )
  (testing "that the display-name for a parallel is just the function-name"
    (is (= "in-parallel" (display-name `in-parallel)))
    (is (= "in-parallel" (display-name (first (first pipeline)))))
  )
)
