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