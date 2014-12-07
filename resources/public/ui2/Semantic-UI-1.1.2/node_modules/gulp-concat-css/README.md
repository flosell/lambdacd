# [gulp](https://github.com/wearefractal/gulp)-concat-css
[![Build Status](https://secure.travis-ci.org/mariocasciaro/gulp-concat-css.png?branch=master)](https://travis-ci.org/mariocasciaro/gulp-concat-css)
[![NPM version](http://img.shields.io/npm/v/gulp-concat-css.svg)](https://www.npmjs.org/package/gulp-concat-css)
[![Dependency Status](https://david-dm.org/mariocasciaro/gulp-concat-css.svg)](https://david-dm.org/mariocasciaro/gulp-concat-css)
[![Downloads](http://img.shields.io/npm/dm/gulp-concat-css.svg)](https://www.npmjs.org/package/gulp-concat-css)

> Concatenate css files, rebasing urls and inlining @import.

## Install

Install with [npm](https://npmjs.org/package/gulp-concat-css).

```
npm install --save-dev gulp-concat-css
```

## Examples

```js
var gulp = require('gulp');
var concatCss = require('gulp-concat-css');

gulp.task('default', function () {
  gulp.src('assets/**/*.css')
    .pipe(concatCss("styles/bundle.css"))
    .pipe(gulp.dest('out/'));
});
```

## License

[MIT](http://en.wikipedia.org/wiki/MIT_License) @ Mario Casciaro
