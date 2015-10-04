# How to do X

## How do I call things on the command line?

You can use the bash command from `lambdacd.steps.shell`:
```clojure
(defn some-build-step [arg ctx]
  (shell/bash ctx "/some/working/directory"
              "./scriptInWorkingDirectory.sh"
              "./anotherScript.sh"
              "echo i-can-call-builtins"))
```

You can also define environment variables:
```clojure
(defn some-build-step [arg ctx]
  (shell/bash ctx "/some/working/directory" {"ENV_VARIABLE" "hello"}
              "echo $ENV_VARIABLE"))
```

## How do I interact with git-repositories?

Git is supported by the `lambdacd.steps.git` namespace.

As a build-trigger, you can use the `wait-for-git` or `wait-with-details` functions (they behave the same, but the latter
also assembles information on the commits it found since the last commit):

```clojure
(defn wait-for-commit [_ ctx]
  (git/wait-with-details ctx "git@github.com:user/project.git" "somebranch"))
```

This function returns the new git-revision under the `:revision` key where it is available for the next build step.

Usually, the next step in your build pipeline would be executing some build steps on this revision of the repository:

```clojure
   wait-for-commit
   (with-repo
     build
     test
     publish)
```

```clojure
(defn with-frontend-git [& steps]
  (fn [args ctx]
    (git/checkout-and-execute "git@github.com:user/project.git" (:revision args) args ctx steps)))
```

There's also a shorthand for this relying on the revision in `:revision`:

```clojure
(defn with-repo [& steps]
  (git/with-git "git@github.com:user/project.git" steps))
```

Both will check out the specified revision into a new temporary workspace and then execute the given steps.
The steps receive the workspace path as an argument under the `:cwd` key.

## How do I write to the output so I can see in the UI what my build-step is doing?

The output displayed in the UI is the string under the `:out` key of your steps result. If you want to update this while
your step is still running, you can do so via the `:result-channel` value of `ctx`.

When you are using any of the built-in helpers in your steps (like `shell/bash`), this is already built in for you.
Otherwise, you can use functions in the `lambdacd.steps.support` namespace to help you do this:

```clojure
(:require [lambdacd.steps.support :refer [new-printer print-to-output printed-output]])

(defn some-step [args ctx]
  ; create a printer that accumulates your output
  (let [printer (new-printer)]
    ; do something and write messages to the output
    (print-to-output ctx printer "Hello")
    (print-to-output ctx printer "World")
    ; return your accumulated output in the :out value of your steps result
    {:status :success
     :out (printed-output printer)})))
```

## How do I generate several pipelines with the same structure?

You might have several pipelines that look pretty much the same. Maybe they check out a repository, run tests and build
an artifact. Duplicating the whole pipeline really seems unnecessary. That's where parameterized pipelines come in.

First of all, you need build steps that can be parameterized. Maybe you'll want a build step that's executing a given script:

```clojure
(defn run-custom-test [test-command]
  (fn [args ctx]
    (shell/bash ctx "/some/working/directory" test-command)))
```

So what happened there? A function returning a function? Yes. The outer function will be evaluated when the pipeline is
initialized and returns the build step (the inner function) that will be executed in the pipeline. Control flow functions
 like `in-parallel` work exactly the same way.

So now we need to get it into the pipeline:

```clojure
(def pipeline-def
 `(
     wait-for-manual-trigger
     (with-repo "some-repo-uri"
       (run-tests "./test-script")
                  publish))
```

Now you have a build step that you can use in more than one pipeline. If your pipelines look different, that's maybe all
you need. But maybe your pipelines all look the same. You wouldn't want to duplicate it all the time, right? So instead
of defining a pipeline statically, let's create a function to generate the pipeline structure:

```clojure
(defn mk-pipeline-def [repo-uri test-command]
  `(
     wait-for-manual-trigger
     (with-repo ~repo-uri
                (run-tests ~test-command)
                publish)))
```

Here's a full example: https://github.com/flosell/lambdacd-cookie-cutter-pipeline-example/tree/master/src/pipeline_templates

## How do I use fragments of a pipeline in more than one pipeline?

As you start building a bigger project, you might feel the need to have some build steps into more than one pipeline.
For example, you want a set of tests executed in all your deployment-pipelines.

As LambdaCD pipelines are little more than nested lists, you can easily inline or concatenate pipeline fragments:

```clojure
(def common-tests
  `((in-parallel
      testgroup-one
      testgroup-two)))

(def service-one-pipeline
  (concat
     `(
     ; some build steps
     )
     common-tests))

(def service-two-pipeline
  (concat
     `(
     ; some build steps
     )
     common-tests))
```

## How do I implement authentication and authorization?

The usual way to interact with a pipeline (apart from committing to a repository the pipeline watches) is through the
web interface so if you want to prevent unauthorized users from triggering actions on your build or from even viewing it,
you must restrict access to the underlying HTTP endpoints.

Since the the `lambdacd.ui-server/ui-for` function returns a normal ring handler, any ring-middleware can be wrapped
around it.

For example, to implement simple HTTP Basic Auth password protection, you can use [ring-basic-authentication](https://github.com/remvee/ring-basic-authentication):
```clojure
(defn authenticated? [name pass]
  (and (= name "some-user")
       (= pass "some-pass")))

(defn -main [& args]
  (let [;; ...
        ring-handler (ui/ui-for pipeline)]
    (ring-server/serve (wrap-basic-authentication ring-handler authenticated?))))
```

For more advanced security, use [friend](https://github.com/cemerick/friend), [clj-ldap](https://github.com/pauldorman/clj-ldap)
or any other clojure library that works as a ring middleware.
