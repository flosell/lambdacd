# Frequently Asked Questions

## What is this?
LambdaCD is a Clojure library that gives you the building blocks to write your own build-server.
It's aim is to replace existing CI/CD tools with custom built pipeline services, defined completely in code.

## Why do I need this?
Because code is a great way to express what we want! When your build pipeline is in code, you'll get

* version control
* refactoring
* testing
* reuse
* dependency management
* flexibility
* power

basically for free!

## Can I use this in my project?
This project is still in it's early phase, so don't rely on everything being just perfect out of the box.
But I do know people are using LambdaCD for serious work so it probably can work on your project as well.

## How do I do X?

See [How To](howto.md)

## Does it support X?
Probably not.
As mentioned above, LambdaCD is a young project so your favorite feature might be missing. If this is the case, feel
free to reach out or open a ticket so I know what people really need.

Or even better: build the feature yourself! LambdaCD is meant to be extensible and since everything in your
build-pipeline is code, lots of things should be straightforward to implement as code that runs in your build-step
(e.g. support for another VCS, test result processing, custom control flow, ...).

## Can I display build results on my information radiator?

There is a separate library to provide expose your build pipeline in cctray.xml format: [lambdacd-cctray](https://github.com/flosell/lambdacd-cctray)
Most build pipeline monitors can read this format so you should be able to plug LambdaCD right in.

## Does LambdaCD support more than one pipeline?
Yes, you can run as many pipelines as you wish in one instance of LambdaCD. Just define a second one and initialize
it just like the first one.

