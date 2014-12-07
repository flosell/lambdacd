'use strict';

var gulp = require('gulp'),
  ghelp = require('../../index.js'),
  jshint = require('gulp-jshint'),
  stylish = require('jshint-stylish'),
  map = require('map-stream'),
  gutil = require('gulp-util'),
  lintPaths = ['./*.js', './lib/*.js'];

ghelp(gulp);

// --------------------------------------------------------------------------------------
// tasks
// --------------------------------------------------------------------------------------

gulp.task('lint', 'Lints all server side js', function () {
  return lint()
    .on('end', function () {
      lintOnEnd();
      if (exitCode) {
        // if lint failed we want to exit gulp with an exit code of 1 so our CI will fail properly
        process.emit('exit');
      }
    });
});

// Task just for watch so that it reports all errors but does not exit the watch process.
// Help text is false because people shouldn't call this one directly.
gulp.task('lint-watch', false, function () {
  return lint()
    .on('end', lintOnEnd);
});

gulp.task('ci', 'Run all CI verification', ['lint']);

// Separate task so "watch" can easily be overridden.
gulp.task('ci-watch', false, function () {
  gulp.watch(lintPaths, ['lint-watch']);
});

gulp.task('watch', 'Watch files and run ci validation on change', ['ci-watch']);

gulp.task('combo', ['ci']);

gulp.task('a-super-long-task-name');
gulp.task('a-super-long-task-name-that-is-ignored-and-not-counted-for-margins', false);

// --------------------------------------------------------------------------------------
// helper functions
// --------------------------------------------------------------------------------------

var totalLintErrors = 0,
  exitCode = 0;

process.on('exit', function () {
  process.nextTick(function () {
    var msg = "gulp '" + gulp.seq + "' failed";
    console.log(gutil.colors.red(msg));
    process.exit(exitCode);
  });
});

// cleanup all variables since, if we're running 'watch', they'll stick around in memory
function beforeEach() {
  totalLintErrors = 0;
  exitCode = 0;
}

function taskPassed(taskName) {
  var msg = "gulp '" + taskName + "' passed";
  console.log(gutil.colors.green(msg));
}

function lint() {
  beforeEach();
  return gulp.src(lintPaths)
    .pipe(jshint())
    .pipe(jshint.reporter(stylish))
    .pipe(map(function (file, cb) {
      if (!file.jshint.success) {
        totalLintErrors += file.jshint.results.length;
        exitCode = 1;
      }
      cb(null, file);
    }));
}

function lintOnEnd() {
  var errString = totalLintErrors + '';
  if (exitCode) {
    console.log(gutil.colors.magenta(errString), 'errors\n');
    gutil.beep();
  } else {
    taskPassed('lint');
  }
}