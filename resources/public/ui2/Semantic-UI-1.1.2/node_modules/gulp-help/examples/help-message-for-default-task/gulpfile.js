'use strict';

var gulp = require('gulp'),
  ghelp = require('../../index.js');

ghelp(gulp);

gulp.task('default', 'Custom message for default task', ['help'], function() {
  console.log('do stuff...');
});