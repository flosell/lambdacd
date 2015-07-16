# LambdaCD

* it's a continuous delivery pipeline, in code
* it's your own custom built Jenkins/Go/TeamCity/..., in clojure

## Status

[![Clojars Project](http://clojars.org/lambdacd/latest-version.svg)](http://clojars.org/lambdacd)

[![Build Status](https://travis-ci.org/flosell/lambdacd.svg)](https://travis-ci.org/flosell/lambdacd)

This project is still in it's early phase, don't rely on everything working flawlessly.
There will be bugs, there will be missing features, things will change and things will look ugly.

However, people do use LambdaCD in their day to day work and are happy it, so give it a try!
And if you notice something, please open bug reports, feature requests or just give feedback!

## Usage

* `lein new lambdacd <NAME>` will create a new pipeline-project
* `lein run` from the project folder starts the server and opens the the UI for your pipeline
* your pipeline is defined in `src/<NAME>/`. Have a look around, change some steps or add some steps on your own.


## Example

```clojure
;; buildsteps
(def some-repo "git@github.com:flosell/somerepo")

(defn wait-for-repo [_ ctx]
  (git/wait-for-git ctx some-repo "master"))

(defn ^{:display-type :container} with-repo [& steps]
  (git/with-git some-repo steps))

(defn run-tests [{cwd :cwd} ctx]
  (shell/bash ctx cwd
    "lein test"))

(defn compile-and-deploy [{cwd :cwd} ctx]
  (shell/bash ctx cwd
    "./buildscripts/compile-and-deploy.sh"))

;; the pipeline
(def pipeline
  `(
     (either
       wait-for-manual-trigger
       wait-for-repo)
     (with-repo
       run-tests
       compile-and-deploy)
   ))

```

## Resources

* [FAQ](doc/FAQ.md)
* [How to do ...](doc/howto.md)
* Detailed Tutorial: [doc/walkthrough.md](doc/walkthrough.md)
* A complete LambdaCD infrastructure in code: https://github.com/flosell/devops-101-lambdacd
* Documented Sample-Code: [example/clj/todopipeline](example/clj/todopipeline)
* Blog Post on LambdaCD at Otto including tutorial: http://dev.otto.de/2015/06/29/microservices-ci-with-lambdacd-microservices-and-continuous-integration-with-lambdacd-23/

## Related projects

* [lambdacd-cctray](https://github.com/flosell/lambdacd-cctray): Support for cctray.xml to integrate LambdaCD with build monitoring tools such as [nevergreen](http://nevergreen.io/) or [CCMenu](http://ccmenu.org/)

## Development

* `./go` is your starting point. Run it without arguments to see all the options, e.g.:
  * `./go testall` runs all tests
  * `./go serve` starts a server and opens pipeline-view showing the example-pipeline in your browser.
* if you want to run the example-pipeline contained in the code successfully, you first need to setup a mock-deployment environment on your machine (two VMs where we deploy a TodoMVC client and server):
  * install [Vagrant](http://www.vagrantup.com/downloads.html)
  * have github-access set up (you need to be able to clone with ssh)
  * `./go setup` starts up two VMs in vagrant where we deploy to and exports the ssh-config for them so that it can be used by the deployment scripts


## Contribute

* File bug reports, give feedback
* Send Pull Requests, or if you are missing features (support for a different version control system, nicer syntactic sugar, ...) consider creating an extension library.

## License

Copyright © 2014 Florian Sellmayr

Distributed under the Apache License 2.0
