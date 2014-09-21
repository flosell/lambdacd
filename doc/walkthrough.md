# Your first build pipeline with LambdaCD

This will guide you through a simple pipeline setup.
LambdaCD being a clojure-library, knowing a bit of clojure helps, but if you aren't a convert yet, give it a shot anyway. If you need help, I provided some [pointers](basic-clojure.md) to help you out.

## The task

As LambdaCD is a tool to implement build-pipelines, in this walkthrough we are going to implement one. Specifically, we are going to implement a pipeline that is pulling a [TodoMVC](http://todomvc.com/) client and backend from GitHub, run tests, assemble publish artifacts and deploy those artifacts onto servers to run from.

The whole thing will look more or less like this:
![Our pipline design](img/pipeline-overview.png)

## Setup

Before we start, I said we wanted to deploy apps to a server, so we probably need a server and some deployment scripts. Fortunately, LambdaCD is using the exact same example during development so there are some things ready for you to use. Basically, the setup is two vagrant boxes to deploy to and some bash scripts that handle most of the deployment.

If you aren't sure if you want to do the whole walkthrough, feel free to skip this step for now and only set up these things once you get to the actual deployment part.

So let's get started.
* Make sure you have [Vagrant](http://www.vagrantup.com/downloads.html) installed
* Make sure you have [Leiningen 2.x](http://leiningen.org/#install) installed
* Clone the [LambdaCD repo](https://github.com/flosell/lambdacd). You normally don't need to do this to create a pipeline, it just so happens that the Vagrant setup is there at the moment.
* in the cloned repo, run `./go setup`. This will run for a while to start your two Vagrant boxes you can deploy to.
* You should now have a fully functioning setup. You can run the example pipeline using `lein ring server` just to make sure.


## Your first pipeline - The template

After all this setup, lets really get started. First of all, we need a new project to develop our pipeline: `lein new lambdacd pipeline-tutorial` will create a new project for you, already set up with some sensible defaults and ready to run.

So let's run it: `cd` ito the newly created `pipeline-tutorial` directory and run `lein ring server`. After a few seconds, this will start up a server listening on port 3000 and your browser will pop up, with the overview-page showing your pipelines current state. Click on the first step (it's a manual trigger) to start the pipeline. Now things start to move and you see a running build.

But where did this all come from?

Let's find out. Open `src/pipeline_tutorial/pipeline.clj`. This is where your pipeline lives. Specifically, `pipeline-def`. This contains a list of clojure functions that are being executed during a pipeline-run. So in this case, it first calls the function `wait-for-manual-trigger` from the `lambdacd.manualtrigger` namespace. This is a function LambdaCD provides to implement the trigger functionality you just saw. After this function returns (after you clicked the trigger), the `some-step-that-does-nothing`-function is called. This is a step that's defined by us (more on that in a minute).

And after that? What is this list doing there with the `in-parallel`?

You can probably already guess it: This is our way of nesting: Lists indicate that the first function in the list (`in-parallel` in this case) will take care of executing the nested steps. We'll get into this in more detail later so for now, just believe me that this will execute the functions echoing "foo" and "bar" in parallel, waiting for both to finish and then continuing with the rest of the pipeline.

So now we know how to set up the structure of the pipeline. But I still haven't told you how the interesting bits work, your build-steps. So here we go:

Build steps are normal functions. They have this contract

* the first parameter `args` contains a map of key-value pairs that were returned by the previous build-step
* the second parameter `context` contains lower-level details you'll need if you are planning to implement your own control-flow operations and other lower level functionality. For now, just ignore it.
* They return a map of key-value parts. This is the data you pass on to the next step as `args`. Most of it can be arbitrary but one you need to have: `:status` needs to be `:success` if you want the pipeline to go on. Pretty much anything else is a failure but `:failure` is convention for this case.

In our example the steps are defined in `src/pipeline_tutorial/steps.clj`. As those steps are so simple, they don't care much about anything so they just ignore any input (`[& _]` is a good shorthand for this) and either return immediately or pass on the "hard" work to the terminal, using the `bash` function. It takes the working directory and an arbitrary number of commands. Change something simple and restart your `lein ring server` to see some changes.

Now we covered most of the code that was generated for you, except for some infrastructure that binds the whole thing together. You don't need to care about this to finish the tutorial but a bit of context might help understand what's going on under the hood.

Back in `pipeline.clj`, you'll find those three lines.
```clojure
(def pipeline (mk-pipeline pipeline-def))

(def app (:ring-handler pipeline))
(def start-pipeline-thread (:init pipeline))
```
The first wires together all the internals of LambdaCD and exposes the interesting parts as a map, accessible as `pipeline`.

The two other lines define the symbols `app` and `start-pipeline-thread`. `app` is a [Ring](https://github.com/ring-clojure/ring) handler, basically the function that handles all the traffic from the HTTP server you just saw.
`start-pipeline-thread` is a function that does just that, starting the pipeline in a seperate thread in the background.

Both of these symbols are referenced from `project.clj` where they are being picked up by Leiningen when you start the server.
