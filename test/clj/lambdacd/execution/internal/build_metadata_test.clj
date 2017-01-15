(ns lambdacd.execution.internal.build-metadata-test
  (:require [clojure.test :refer :all]
            [lambdacd.execution.internal.build-metadata :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [shrubbery.core :refer [mock received?]]
            [lambdacd.state.protocols :as state-protocols]))


(defn swap-metadata! [ctx f]
  (swap! (:build-metadata-atom ctx) f))

(defn get-metadata [ctx]
  @(:build-metadata-atom ctx))

(def some-build-number 42)

(deftest build-metadata-test
  (testing "that metadata is initially empty"
    (is (= {} (get-metadata (add-metadata-atom (some-ctx))))))
  (testing "that changing metadata fails if the new value is not a map"
    (let [ctx (add-metadata-atom (some-ctx))]
      (is (thrown? Exception (swap-metadata! ctx (constantly "some-string"))))))
  (testing "that we can get and update metadata"
    (let [ctx (add-metadata-atom (some-ctx))]
      (swap-metadata! ctx #(assoc % :some :metadata))
      (is (= {:some :metadata} (get-metadata ctx)))))
  (testing "that build metadata updates are being consumed by the state component"
    (let [state-component (mock state-protocols/BuildMetadataConsumer)
          ctx             (add-metadata-atom (some-ctx-with :pipeline-state-component state-component
                                                            :build-number some-build-number))]
      (swap-metadata! ctx #(assoc % :some :metadata))
      (is (received? state-component state-protocols/consume-build-metadata [some-build-number {:some :metadata}])))))
