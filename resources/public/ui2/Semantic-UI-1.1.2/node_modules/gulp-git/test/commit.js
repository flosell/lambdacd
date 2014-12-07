/* global describe, it, after, before, afterEach, beforeEach */

var fs = require('fs');
var path = require('path');
var rimraf = require('rimraf');
var should = require('should');
var gutil = require('gulp-util');

module.exports = function(git, util){

  it('should commit a file to the repo', function(done) {
    var fakeFile = util.testFiles[0];
    var opt = {cwd: './test/repo/'};
    var gitS = git.commit('initial commit', opt);
    gitS.once('finish', function(){
      setTimeout(function(){
        fs.readFileSync(util.testCommit)
          .toString('utf8')
          .should.match(/initial commit/);
        done();
      }, 100);
    });
    gitS.write(fakeFile);
    gitS.end();
  });

};
