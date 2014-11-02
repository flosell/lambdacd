# LambdaCD

* it's a continuous delivery pipeline, in code
* it's Jenkins/Go/TeamCity/..., in clojure

[![Build Status](https://travis-ci.org/flosell/lambdacd.svg)](https://travis-ci.org/flosell/lambdacd)

## Status

This project is still in it's early prototype/proof of concept/experimentation stage, so don't rely on everything working flawlessly. There will be bugs, there will be missing features, things will change and things will look ugly.

Nevertheless, give it a try, send in bug reports, feature requests or just give feedback!

## Usage

* `lein new lambdacd <NAME>` will create a new pipeline-project
* `lein ring server` from the project folder starts the server and opens the the UI for your pipeline
* your pipeline is defined in `src/<NAME>/`. Have a look around, change some steps or add some steps on your own.
* for details, check out the [walkthrough](doc/walkthrough.md) or dive straight into the [documented sample code](http://flosell.github.io/lambdacd/marginalia/uberdoc.html).


## Development

* `lein test :all` runs all tests
* if you want to run the example-pipeline contained in the code, you first need to setup a mock-deployment environment on your machine (two VMs where we deploy a TodoMVC client and server):
  * install [Vagrant](http://www.vagrantup.com/downloads.html)
  * have github-access set up (you need to be able to clone with ssh)
  * `./go setup` starts up two VMs in vagrant where we deploy to and exports the ssh-config for them so that it can be used by the deployment scripts
  * `lein ring server` starts a server and opens pipeline-view showing the example-pipeline in your browser.


## Contribute

* File bug reports, give feedback
* Send Pull Requests, or if you are missing features (support for a different version control system, nicer syntactic sugar, ...) consider creating an extension library.

## License

Copyright © 2014 Florian Sellmayr

Distributed under the Apache License 2.0
