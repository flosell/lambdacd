(ns lambdacd.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [lambdacd.history-test]
            [lambdacd.output-test]
            [lambdacd.pipeline-test]
            [lambdacd.route-test]
            [lambdacd.state-test]
            [lambdacd.time-test]
            [lambdacd.ui-core-test]
            [lambdacd.db-test]))

(doo-tests 'lambdacd.history-test
           'lambdacd.output-test
           'lambdacd.pipeline-test
           'lambdacd.route-test
           'lambdacd.state-test
           'lambdacd.time-test
           'lambdacd.ui-core-test
           'lambdacd.db-test)
