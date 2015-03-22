//-------------------------------------------------------------------------------------------------
//
// I am a "runner" script for use with phantomjs and cemerick/clojurescript.test
// I handle the case where the cljsbuild setting is ':optimizations :none'
//
// See: https://github.com/mike-thompson-day8/cljsbuild-none-test-seed
//
var fs       = require('fs');
var system   = require('system');
var thisName = system.args[0].split('/').slice(-1);

var usage = [
   "",
   "Usage: phantomjs " + thisName + " output-dir  output-to  [tweaks]",
   "",
   "Where:",
   "    - \"output-dir\" and \"output-to\" should match the paths you supplied ",
   "      to cljsbuild in the project.clj. (right next to \":optimizations :none\").",
   "    - [tweaks] is zero or more of either:",
   "        (1) an extra javascript file - e.g.  path/to/my-shim.js ",
   "        (2) arbitrary javascript code fragments. E.g. window.something=flag",
   "      These tweaks will be applied to the test page prior to load of test code."
   ].join("\n");


//-- Colors ---------------------------------------------------------------------------------------

function yellow(text) {
    return "\u001b[31m" + text + "\u001b[0m";
}

function red(text) {
    return "\u001b[33m" + text + "\u001b[0m";
}

function green(text) {
    return "\u001b[32m" + text + "\u001b[0m";
}


//-- Commandline  ---------------------------------------------------------------------------------

if (system.args.length < 3)  {
    console.log(usage);
    phantom.exit(1);
}

// google base dir
var output_dir = system.args[1];
if (output_dir.slice(-1) != "/")    // we want a trailing '/'
    output_dir = output_dir + "/";
if (!fs.isDirectory(output_dir)) {
    console.log(red('\nError: output_dir directory doesn\'t exist: '  + output_dir))
    phantom.exit(1)
}


var googBasedir = output_dir + "goog/"
if (!fs.isDirectory(googBasedir)) {
    console.log(red('\nError: goog directory doesn\'t exist: '  + googBasedir))
    phantom.exit(1)
}

var BASE_JS = googBasedir + "base.js";
if (!fs.exists(BASE_JS)) {
    console.log(red('\nError: base.js doesn\'t exist: ' + BASE_JS));
    phantom.exit(1)
}

var DEPS_JS = googBasedir + "deps.js";
if (!fs.exists(DEPS_JS)) {
    console.log(red('\nError: deps.js doesn\'t exist: ' + DEPS_JS));
    phantom.exit(1)
}

// test file
var testFile = system.args[2];    // output-to parameter. Eg  test.js
if (!fs.exists(testFile)) {
    console.log(red('\nError: test file doesn\'t exist: ' + testFile));
    phantom.exit(1)
}


//-- Initialise Test Page -------------------------------------------------------------------------

// We'll do our testing in this page.
var page = require('webpage').create();

// When the test page produces console output, make it visible to the user, with colours.
page.onConsoleMessage = function (line) {
    if (line === "[NEWLINE]")
        return;

    line = line.replace(/\[NEWLINE\]/g, "\n");

    // add colour
    if (-1 != line.indexOf('ERROR')) {
         line = red(line);
    }
    else if (-1 != line.indexOf('FAIL')) {
         line = yellow(line);
    }
    else if (-1 != line.indexOf('Testing complete')) {
         line = green(line);
    }

    console.log(line);
};


page.onError = function(msg, trace) {
    var msgStack = ['ERROR: ' + msg];
    if (trace) {
        msgStack.push('STACK TRACE:');
        trace.forEach(function(t) {
            msgStack.push(' -> ' + (t.file || t.sourceURL) + ': ' + t.line +
                          (t.function ? ' (in function ' + t.function + ')' : ''));
        });
    }
    console.log(red(msgStack.join('\n')));

    var runAllTestsIsDefined = page.evaluate(function() {
        return (typeof cemerick !== "undefined" &&
            typeof cemerick.cljs !== "undefined" &&
            typeof cemerick.cljs.test !== "undefined" &&
            typeof cemerick.cljs.test.run_all_tests === "function");
    });
    if  (!runAllTestsIsDefined)  {
        var messageLines = [
            "",
            "Possible cause: the namespace cemerick.cljs.test isn't defined.",
            "",
            "To resolve: ensure [cemerick.cljs.test] appears",
            "in the :require clause of your test suite.",
            "Also, ensure there's one or more test files.",
        ];
        console.error(messageLines.join("\n"));
    }

    phantom.exit(1);
}


//-- Handle Any Tweaks  -----------------------------------------------------------------------------


for (var i = 3; i < system.args.length; i++) {
    var arg = system.args[i];
    if (fs.exists(arg)) {
        if (!page.injectJs(arg))
          throw new Error("Failed to inject " + arg);
    } else {
        // if used in notify command another arguement is passed that is 
        // the results of the compilation
        if ((arg.indexOf("Successfully compiled") != 0) &
            (arg.indexOf("Compiling \"") !=0)) {
          page.evaluateJavaScript("(function () { " + arg + ";" + " })");
        }
    }
}


//-- Plan Of Action -------------------------------------------------------------------------------
//
//
// With the ':optimisation :none' setting, 'test.js' will only contain dependency
// information. Inspect the file and you'll only see calls to goog.addDependency().
// All the real code (which we want loaded into our test page) is in the
// js files referenced.
//
// So this script has to:
//    - load and interpret the dependency information in test.js
//      To do that we need to use 'goog' (Google Closure runtime)
//      And that means importing base.js into this phantom context.
//    - inject unittest javascript into the testing page IN THE CORRECT DEPENDENCY ORDER.
//      Again, we have to use goog, plus some monkey patching.
//    - run the "test-runner" suppied by cemerick/clojurescript.test
//    - organise that runner output is correctly displayed for the world to see.
//
//

//-- Load Google Clojure ----------------------------------------------------------------------------

// We need 'goog' in this phantom context, so we can interpret the dependencies in test.js
phantom.injectJs(BASE_JS)

// Load the two "dependencies" files into the phantom content (not the page context!!)
// these two files contain lots of addDependancy calls
phantom.injectJs(DEPS_JS)
phantom.injectJs(testFile)

// Tell goog that javscript imports should be interpreted as js injections into the test page
goog.global.CLOSURE_IMPORT_SCRIPT = function(path) {
    page.injectJs(googBasedir + path);
    return true;
};



//-- Load code into our test page  ----------------------------------------------------------------

// we need 'goog' in the test page because there'll be references to it within the javascript we load
page.injectJs(BASE_JS);

// This loop is where a lot of important work happens
// It will inject both the unittests and code-to-be-tested into the page
//
// The loop was originally this simple:
//     for(var namespace in goog.dependencies_.nameToPath)
//         goog.require(namespace);
//
// But then this happened: http://dev.clojure.org/jira/browse/CLJS-995
//
// Now we require in all namespaces which depend on `cljs.core`
for(var path in goog.dependencies_.requires) {
    if (goog.dependencies_.requires[path]["cljs.core"]) {              // a cljs file ?
        for (var namespace in goog.dependencies_.pathToNames[path])    // find the associated namespaces - there should only ever be one
            goog.require(namespace);          // will trigger CLOSURE_IMPORT_SCRIPT calls which injectJs into page
    }
}


//-- Run the tests  -------------------------------------------------------------------------------
//
// All the code is now loaded into the test page. Time to test.

// Hack: use an alert handler to detect when the tests are finished.
var specialMarker = "phantom-exit-code:";
page.onAlert = function (msg) {
    var exitCode = msg.replace(specialMarker, "");
    if (msg != exitCode)
        phantom.exit(parseInt(exitCode));
};


page.evaluate(function (specialMarker) {

      cemerick.cljs.test.set_print_fn_BANG_(function(x) {
          console.log(x.replace(/\n/g, "[NEWLINE]"));        // since console.log *itself* adds a newline
      });

      var results = cemerick.cljs.test.run_all_tests();

      cemerick.cljs.test.on_testing_complete(results, function () {
          window.alert(specialMarker + (cemerick.cljs.test.successful_QMARK_(results) ? 0 : 1));
      });

    },
    specialMarker);

