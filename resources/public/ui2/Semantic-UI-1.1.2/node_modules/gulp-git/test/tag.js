/* global describe, it, after, before, afterEach, beforeEach */

var fs = require('fs');
var path = require('path');
var rimraf = require('rimraf');
var should = require('should');
var gutil = require('gulp-util');

module.exports = function(git, testFiles, testCommit){

  // These must be run on a system which has git installed
  // no pull delay, and has git configured.

  /*
  it('should tag a version of the repo', function(done) {

    git.tag('v1.2.3', 'message', {cwd: "./test/"}, function() {
      should.exist('test/.git/refs/tags/v1.2.3');
      done();
    });

  });
  */

};
