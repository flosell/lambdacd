(ns lambdacd.internal.step-id
  (:require [lambdacd.util :as util]
            [lambdacd.step-id :as step-id]))

; THIS NAMESPACE IS DEPRECATED and will be removed in subsequent releases.
; Use lambdacd.step-id instead.

(defn parent-of? [a b]
  (step-id/parent-of? a b))

(defn later-than? [a b]
  (step-id/later-than? a b))

(defn before? [a b]
  (step-id/later-than? a b))

(defn child-id [parent-step-id child-number]
  (step-id/child-id parent-step-id child-number))

(defn root-step-id? [step-id]
  (step-id/root-step-id? step-id))

(defn root-step-id-of [step-id]
  (step-id/root-step-id-of step-id))