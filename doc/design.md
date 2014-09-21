# Design

LambdaCD is designed to be a library that provides you with the essential building-blocks to develop a small application that is your build-pipeline. You would then deploy this application like you would deploy any other application or piece of build-infrastructure.

In this world, a build pipeline is just a sequence of clojure-functions (the build-steps) that implement a certain convention, i.e. they receive a map of parameters and return a map of output-values. We use this design for everything in the pipeline:
* triggers are build-steps that wait for something (e.g. a new git-commit) before returning
* containers are build-steps that coordinate a set of child-steps and control their execution (e.g. to execute a set of steps in parallel)

TODO: go on here?
