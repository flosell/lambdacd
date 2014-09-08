# LambdaCD

* it's a continuous delivery-pipeline, in code
* it's Jenkins/Go/Teamcity/..., in clojure

## Design

LambdaCD is designed to be a library that provides you with the essential building-blocks to develop a small application that is your build-pipeline. You would then deploy this application like you would deploy any other application or piece of build-infrastructure.

Those building blocks are:
* steps: Build-steps are normal clojure-functions that receive a map of arguments, perform action (like checking out a repo, running tests or deploying your application) and return a map of result-values.
* triggers: Are build-steps that wait until some condition (e.g. a commit is pushed to a repo, a user has triggered manually) is true before returning and thereby triggering the next step in the pipeline.
* container steps: are build-steps that control the way their child-steps are being executed. It may for example execute them in parallel, set arguments for all of them or only run them under certain conditions.
* pipeline: Is a list of steps that will be executed by the execution-engine

TODO: the building blocks aren't really building blocks, they are the part of the pipeline... we should really be talking about the pipeline-visualization, the execution-engine, the helper-functions...


## Status

This project is still in it's early prototype/proof of concept/experimentation stage, so don't rely on everything working flawlessly. There will be bugs, there will be missing features, things will change and things will look ugly.

Nevertheless, give it a try, send in bug reports, feature requests or just give feedback!

## Usage

* TODO: maybe we should start with a lein template here?
* TODO: the things in the trello task

## Development

* `lein test` runs all tests
* if you want to run the example-pipeline contained in the code, you first need to setup a mock-deployment environment on your machine (two VMs where we deploy a TodoMVC client and server):
  * install [Vagrant](http://www.vagrantup.com/downloads.html)
  * have github-access set up (you need to be able to clone with ssh)
  * `./go setup` starts up two VMs in vagrant where we deploy to and exports the ssh-config for them so that it can be used by the deployment scripts
  * `lein ring server` starts a server and opens pipeline-view showing the example-pipeline in your browser.


## Contribute

* TODO

## License

Copyright © 2014 Florian Sellmayr

Distributed under the Apache License 2.0
