(ns lambdacd.smoketest.pipeline
  (:require [lambdacd.steps.control-flow :refer [in-parallel]]
            [lambdacd.smoketest.steps :refer :all]
            [lambdacd.util.internal.temp :as temp-util]))

(def pipeline-def
  `(
     lambdacd.steps.manualtrigger/wait-for-manual-trigger
     wait-for-some-repo
     (with-some-repo
       read-some-value-from-repo)
     (in-parallel
       increment-counter-by-three
       increment-counter-by-two
       use-global-value)
     lambdacd.steps.manualtrigger/wait-for-manual-trigger))

(def config
  {:home-dir (temp-util/create-temp-dir)})
