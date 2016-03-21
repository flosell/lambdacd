# Basic Clojure

To try out LambdaCD, here's what you need to know:

* How to install [Leiningen](http://leiningen.org/#install) (this is the build-tool we are using, like Maven in Java)
* Syntax
  * `(foo "bar" 5)`: this executes the function `foo` with the arguments `"bar"` (which is a string) and `5` (an integer)
  * `{ :foo "bar" }`: this is a map with a key `:foo` mapping to a value `"bar"`
  * `(defn add [a b] (+ a b))`: this defines a function `add` with two parameters `a` and `b` that returns the sum of `a` and `b`

* You'll also need an editor. [LightTable](http://www.lighttable.com/) is very popular at the moment, but you can really use anything. If you are used to IntelliJ, try out the [Cursive](https://cursiveclojure.com/userguide/)

## References

This will most likely not be enough so here are some places with more detailed infos:

* http://www.clojurenewbieguide.com/
* http://tryclj.com/
* http://clojure-doc.org/articles/tutorials/introduction.html
* http://www.braveclojure.com/
