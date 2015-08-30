# Changelog

This changelog contains a loose collection of changes in every release. I will also try and document all breaking changes to the API.
However, as this is still an experimental library, breaking changes may occur without warning between 0.X.Y releases.

The official release will have a defined and more stable API. If you are already relying on a particular API, please let me know.

## 0.5.1
* Improvements:
  * Made `:display-type :container` the default for container steps, no longer throwing incomprehensible exceptions
    when display-type declarations are forgotten (#43)
  * Now supporting steps with parameters in the pipeline:
   
    ```clojure
    (defn mk-pipeline-def [{repo-uri :repo-uri test-command :test-command}]
      `(
         wait-for-manual-trigger
         (with-repo ~repo-uri
                    (run-tests ~test-command)
                    publish)))
    ```
  * Polished the UI a bit

## 0.5.0

* Improvements:
  * UI: add support for :details field in step-results to display details about a build step result and link to more 
    detailed information (e.g. test reports) (#37)
  * UI: packaged icon font instead of relying on external CDN
  * UI: added vendor prefixes in CSS to improve browser support (esp. Firefox and Safari) 
  * Extracted common functions from `internal.default-pipeline-state` so they can be reused in other persistence components (#38)
  * Generalized pipeline-state-updater to be started by `assemble-pipeline` and pushes updates to any pipeline-state 
    component that is configured (#38)
  * support alternative pipeline-state component in `assemble-pipeline` (#40)
  * fixed bug where `either` didn't kill remaining steps after finishing
  * REST api endpoint `/api/builds/<buildnumber>/` now returns status 404 in case the build does not exist instead of
    returning a half empty data-structure (#42)
* API changes: 
  * Remove deprecated access to the internal pipeline-state through `:state` in the result of assemble-pipeline
  * Remove deprecated `:step-results-channel`
  * `pipeline-state-updater` now started by assemble-pipeline (see above), pipeline-state component should no longer 
    start their own update mechanism (#38)

## 0.4.3

Housekeeping release: Contains mostly cleanup under the hood and changes to APIs for advanced users.
 If you are using custom control-flow steps, runners, persistence mechanisms or other advanced features, make sure you
 look through the changes and upgrade as future releases will remove deprecated functionality.

* Improvements:
  * Now providing events `:step-result-updated` and `:step-finished` on event-bus for use by other components like
    runners and persistence components (#38)
  * Clarified interface for `core/execute-step`
* API changes:
  * The `:step-results-channel` is now deprecated, unsupported and will be removed in subsequent releases.
    Use the new `:step-result-updated` event and filter on `:step-id` to receive updates from child-steps while they run.
  * `(ui-server/ui-for pipeline-def pipeline-state ctx)`  is now deprecated and will be removed in subsequent releases.
    Use `(ui-server/ui-for pipeline)` instead.
  * Direct access to the pipeline-state atom, e.g. through `:state` in the result of assemble-pipeline is now deprecated
    and will be removed in subsequent releases. Use the event-bus or access the state through the `PipelineStateComponent`
    protocol instead.
  * Removed `default-pipeline-state/notify-when-no-first-step-is-active`.
    Use the new events (see above) to be notified about changes to the state of the pipeline

## 0.4.2

* Improvements:
  * Increased time between git-polls in `git/wait-for-git` and `git/wait-for-details` to 10 seconds and
    made that value configurable with an optional parameter `:ms-between-polls`, e.g.

    ```clojure
    (git/wait-with-details ctx some-repo-uri "master" :ms-between-polls 60000)
    ```
  * Added a feature to kill running steps on user request (#31)

## 0.4.1
* Improvements:
  * Added `junction` control flow step that adds a way to model if-then-else logic in a pipeline (#28,#32)
  * No longer shipping a `logback.xml` in the published jar.

## 0.4.0

* Improvements:
  * Cleaned up status inheritance to make it more consistent
  * Support variable arguments instead of step-vector as input for `chain-steps` (#29)
  * Internal refactorings to prepare for more component-oriented structure
  * Contexts always contain `:step-results-channel` which can be used to access a stream of step-state updates,
    e.g. to create custom persistence components or runners (still experimental)
  * Cleaned up dependencies
* Bug fixes:
  * Fix bug in retriggering that left next step in undefined state (#26)
* API changes:
  * Steps returning no `:status` will now be treated as failures instead of receiving status `:undefined`
  * Removed deprecated `:result-channel` argument for `execute-step`
  * Removed deprecated `core/new-base-context-for`
  * `core/execute-step` does no longer output the result-channel data to a `:result-channel` in ctx.
    Was replaced with `:step-results-channels` which provides a stream of complete, aggregated step-result data
  * Calling `lambdacd.steps.support/chain-steps` with a vector instead of just the steps is now deprecated (#29)

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
