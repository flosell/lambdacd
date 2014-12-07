'use strict';

var gulp = require('gulp'),
  ghelp = require('../../index.js');

ghelp(gulp);

// --------------------------------------------------------------------------------------
// tasks
// --------------------------------------------------------------------------------------

gulp.task('build', 'build assets', [], function () {
  console.log('building...');
}, {
  options: {
    'dev': 'Set build type to DEV',
    'ist': 'Set build type to IST',
    'qa': 'Set build type to QA'
  }
});

gulp.task('lint', 'Lints all server side js', function () {
  console.log('linting...');
});

gulp.task('ci', 'Run all CI verification', ['lint']);

// Separate task so "watch" can easily be overridden.
gulp.task('ci-watch', false, function () {
  gulp.watch('./lib/**/*.js', ['lint']);
});

gulp.task('watch', 'Watch files and run ci validation on change', ['ci-watch']);

gulp.task('combo', ['ci']);

gulp.task('a-super-long-task-name', ['build']);
gulp.task('a-super-long-task-name-that-is-ignored-and-not-counted-for-margins', false, ['build']);
gulp.task('a-super-long-task-name-2', 'testing', ['build'], function () {
}, {
  options: {
    'a-super-long-options-name-to-test-margin': 'cool description bro, now make me a sammich'
  }
});

gulp.task('version', 'prints the version.', [], function () {
  // ...
}, {
  options: {
    'env=prod': 'description of env, perhaps with available values',
    'key=val': 'description of key & val'
  }
});