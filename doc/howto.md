# How to do X

## Table of Contents

* [How do I call things on the command line?](#how-do-i-call-things-on-the-command-line)
* [How do I interact with git\-repositories?](#how-do-i-interact-with-git-repositories)
* [How do I write to the output so I can see in the UI what my build\-step is doing?](#how-do-i-write-to-the-output-so-i-can-see-in-the-ui-what-my-build-step-is-doing)
* [How to I display LambdaCD builds on my build monitor?](#how-to-i-display-lambdacd-builds-on-my-build-monitor)
* [How do I generate several pipelines with the same structure?](#how-do-i-generate-several-pipelines-with-the-same-structure)
* [How do I use fragments of a pipeline in more than one pipeline?](#how-do-i-use-fragments-of-a-pipeline-in-more-than-one-pipeline)
* [How do I implement authentication and authorization?](#how-do-i-implement-authentication-and-authorization)
* [How do I deploy my pipeline?](#how-do-i-deploy-my-pipeline)

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

For details, check out the documentation in the [wiki](https://github.com/flosell/lambdacd/wiki/Git)

## How do I write to the output so I can see in the UI what my build-step is doing?

LambdaCD comes with a small utility that can rebind `*out*` to pipe the clojure standard out into LambdaCD: 

```clojure
(:require [lambdacd.steps.support :refer [capture-output]])

(defn some-step [args ctx]
  ; create a printer that accumulates your output
  (capture-output ctx
    ; do something and write messages to the output
    (println "Hello")
    (println "World")
    ; return your accumulated output in the :out value of your steps result
    {:status :success}))
```
Note that this utility currently only supports clojure code that is writing to `*out*`. Code that's writing directly to 
Javas `System.out` is not supported. Feel free to re-open #60 if support for Java Interop is something you urgently need. 

If you need more control over the output, you can write directly to the [result channel](https://github.com/flosell/lambdacd/wiki/Build-Context-\(ctx\))
or use the printer-utilities: 

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

## How to I display LambdaCD builds on my build monitor?

Most build monitoring tools (e.g. [CCMenu](http://ccmenu.org/), [BuildNotify](https://bitbucket.org/Anay/buildnotify/wiki/Home), [nevergreen](http://nevergreen.io/))
support the cctray XML format. LambdaCD can expose the state of your build pipeline steps using the [lambdacd-cctray](https://github.com/flosell/lambdacd-cctray) extension.

You can find a full example [here](https://github.com/flosell/lambdacd-cctray/blob/master/test/lambdacd_cctray/sample_pipeline.clj)

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

## How do I deploy my pipeline?

"OK", you say, "you convinced me that my pipeline is a piece of software, but how to I deploy it?". 

You are right, your pipeline is a piece of software and it shouldn't just run on your laptop, it should be a running on 
a server. It's replacing a build server like Jenkins after all! In fact, it should have a pipeline of it's own. 

LambdaCD allows you to have as many pipelines as you like in your project so the straightforward thing to do is to create
a second pipeline that deploys the pipeline-project itself (a "meta-pipeline"). It can run when the pipeline code changes
and redeploy your pipeline project. 

That still leaves the question on how to really get your pipeline running on a server. This will depend very much on the
infrastructure and the conventions you are working with. In short, deploy your pipeline like you would deploy your application: 
If your applications are deployed as RPM or DEB packages using Puppet, Chef or Ansible, then do the same with your pipeline. 
If you are building AMIs and spin up servers on EC2, do that. 
 
And if you don't have much infrastructure automation in place yet, a bit of shell might be enough as well: run `lein uberjar` 
to create a self-contained JAR file, copy it to a well known location on the server. Have a script to start it from there and kill the old process. 

## Can I customize the UI?

While many parts of the UI are rendered on the client side and can currently hardly be customized, you can customize how
the UI page is rendered on the server side, e.g. to add additional styling, scripts or DOM elements. 

To do that, you need to create your own ui-routes that point to your custom UI instead of using the ones provided by `lambdacd.ui.ui-server/ui-for`  

For an example that adds a navigation bar, check out https://github.com/flosell/lambdacd-cookie-cutter-pipeline-example/blob/header-with-navigation/src/pipeline_templates/custom_ui.clj