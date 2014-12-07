'use strict';

var gulp = require('gulp'),
  jshint = require('gulp-jshint'),
  stylish = require('jshint-stylish'),
  gutil = require('gulp-util'),
  mocha = require('gulp-mocha'),
  nicePackage = require('gulp-nice-package');

function errorLogger(err) {
  gutil.beep();
  gutil.log(err.message);
}

function lint() {
  return gulp.src([
    './*.js',
    './lib/**/*.js'
  ])
    .pipe(jshint('.jshintrc'))
    .pipe(jshint.reporter(stylish))
    .pipe(jshint.reporter('fail', { verbose: true }))
    .on('error', errorLogger);
}

function test() {
  return gulp.src('./tests.js')
    .pipe(mocha({reporter: 'dot'}))
    .on('error', errorLogger);
}

gulp.task('lint', function () {
  return lint();
});

gulp.task('test', function () {
  return test();
});

// when watching, do NOT return the stream, otherwise watch won't continue on error
gulp.task('lint-watch', function () {
  lint();
});
gulp.task('test-watch', function () {
  test();
});

gulp.task('watch', function () {
  gulp.watch([
    './*.js'
  ], ['test-watch', 'lint-watch']);
});

gulp.task('nice-package', function () {
  return gulp.src('package.json')
    .pipe(nicePackage({
      recommendations: false
    }));
});

gulp.task('default', ['watch']);
gulp.task('ci', ['lint', 'test', 'nice-package']);