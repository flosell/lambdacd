# Changelog

This changelog contains a loose collection of changes in every release. I will also try and document all breaking changes to the API.
However, as this is still an experimental library, breaking changes may occur without warning between 0.X.Y releases.

The official release will have a defined and more stable API. If you are already relying on a particular API, please let me know.

## 0.4.0

* Bug fixes
  * Fix bug in retriggering that left next step in undefined state (#26)
* API changes:
  * Removed deprecated `:result-channel` argument for `execute-step`
  * Removed deprecated `core/new-base-context-for`
  * `core/execute-step` always requires a `:result-channel` in `ctx`.
    This is already the case when used in conjunction with `execute-steps`

## 0.3.2

* Improvements:
  * Remove styling for undefined step-status (#23)
  * All container steps inherit their childrens status by default (#24)
  * Add a `run`-container step that can group nested steps (#21)
  * Ignore step-results cloned by retriggering in determining start and stop timestamps in pipeline history (#22)
  * UI: build-history now in descending order (i.e. recent builds first) (#25)
* API changes:
  * the `:result-channel` argument for `execute-step` is now deprecated. Pass custom result-channels in via the ctx instead
  * Generating a new base context moved to `execute-step`, therefore `core/new-base-context-for` is now deprecated, just use `ctx` instead

## 0.3.1

* Bug fixes:
  * Fix updated scrolling (#18)

## 0.3.0

* Bug fixes: #14, #15, #17
* Improvements:
 * Display status in output at the end of a build step (#4)
 * Display total build time in history (#16)
 * Indicate lost connection to LambdaCD in UI
 * Allow retriggering of nested steps (#5)
 * Redirect to new build when retriggering
 * Support hardcoded result-maps in `chain`-macro
 * Support setting environment-variables in `shell/bash` (#9)
* API Changes:
 * removed deprecated argument lists for `lambdacd.internal.execution/execute-steps`


## 0.2.3

* Bug fixes: #13

## 0.2.2

* Bug fixes: #8, #10, #11, #12
* New features:
  * `support/print-to-output` simplifies printing to the output-channel within a step
  * `support/printed-output` in return allows you to get everything that was printed within that step, typically to return at the end of a step
* Improvements:
  * `with-git` removes its workspace after all child-steps are finished
  * Display build status in history (#3)
* API Changes
  * `lambdacd.internal.execution/execute-steps` is now deprecated. use the public `lambdacd.core/execute-steps` instead. (#7)
    `lambdacd.core/execute-steps` takes keyword-arguments instead of argument lists for optional parameters.
  * `lambdacd.internal.execution/execute-step` is now deprecated. use the public `lambdacd.core/execute-step` instead. (#7)

## 0.2.1

* UI: support safari and other older browsers
* Step-Chaining now passes on the initial arguments to all the steps to enable users to pass on parameters that are
  relevant for all steps, e.g. a common working directory.
* `wait-for-git` no longer returns immediately when no last commit is known. Instead just waits for the first commit.
  This behavior seems more intuitive since otherwise on initial run, all build pipelines would start running.
* Git-Support: added `with-commit-details`, a function that enriches the `wait-for-git` result with information about the
  commits found since last build and a convenience function `wait-with-details` that wraps both.

## 0.2.0

* Recording start and update timestamps for every build step
* Cleanup old endpoints: `/api/pipeline` and `/api/pipeline-state`
* Improve retriggering: Create a new pipeline-run instead of overwriting existing builds
* Make bash-step killable
* Breaking Changes:
 * removed `core/mk-pipeline` as a method to initialize the pipeline.
   Replaced with a more flexible `core/assemble-pipeline` and a few single functions that take over things like running
   the pipeline and providing ring-handlers to access the pipeline. For details, see examples.

## 0.1.1

* Improvements in UI

## 0.1.0

* LambdaCD now requires Clojure 1.6.0

## 0.1.0-alpha13

* Major restructuring: (`scripts/migrate-to-new-package-structure.sh` can help rewrite your code to work with the new structure)
  * `control-flow`, `git`, `manualtrigger` and `shell` are now in `steps`-package
  * `lambdacd.presentation` is now `lambdacd.presentation.pipeline-structure`
  * some functions more concerned with presentation than actual management of the pipeline state are moved from `lambdacd.pipeline-state` to `lambdacd.presentation.pipeline-state`
  * internals are now in the `internal` package
  * ui related functionality is now in the `ui` package
  
## 0.1.0-alpha12

## 0.1.0-alpha11

* Removed support for steps returning channels (deprecated since alpha8)

## 0.1.0-alpha10

## 0.1.0-alpha9

## 0.1.0-alpha8

* steps now receive a `:result-channel` value through the context to send intermediate values.
  use this channel instead of returning a result-channel from the step. the latter is now DEPRECATED and will be removed in the next release.
* `bash` now needs the context as first parameter:

  ```clojure
  (shell/bash ctx cwd
      "lein test")
  ```


## 0.1.0-alpha7

no breaking API changes

## 0.1.0-alpha6

* `wait-for-git` now receives the context as first parameter: `(wait-for-git ctx some-repo some-branch)`
  The context must contain a configuration with the `:home-dir` set to a directory where the step can store a file with the last seen revisions (see below)
* `mk-pipeline` requires and additional parameter, the configuration-map (which is where you set the home-dir)

## 0.1.0-alpha5

* just a bugfix for the git-handling 

## 0.1.0-alpha4

* Pipeline-Steps can now return a core-async channel to continously update their state while the step is running (e.g for long-running steps that need to indicate progress on the UI):
 
 ```clojure 
 (async/>!! ch [:out "hello"])
 (async/>!! ch [:out "hello world"])
 (async/>!! ch [:status :success])
 (async/close! ch)
 ```
 
* Dropped support for core-async channels as `:status` value in a steps result-map. Use channels for the whole result instead (see above)

## 0.1.0-alpha3

* parameters for steps changed. the second argument is now a context-map that contains the step-id and
  other low-level information
    * previously `(defn some-step [args step-id])`
    * now `(defn some-step [args {:keys [step-id] :as ctx}])`
* `start-pipeline-thread` has been removed as the initializing function. use the new setup-functionality using `mk-pipeline`:

 ```clojure
 (def pipeline (core/mk-pipeline pipeline-def))

 (def app (:ring-handler pipeline))
 (def start-pipeline-thread (:init pipeline))
 ```
