# [gulp](http://gulpjs.com)-debug [![Build Status](https://travis-ci.org/sindresorhus/gulp-debug.svg?branch=master)](https://travis-ci.org/sindresorhus/gulp-debug)

> Debug [vinyl](https://github.com/wearefractal/vinyl) file streams

![](screenshot.png)


## Install

```sh
$ npm install --save-dev gulp-debug
```


## Usage

```js
var gulp = require('gulp');
var debug = require('gulp-debug');

gulp.task('default', function () {
	return gulp.src('foo.js')
		.pipe(debug({verbose: true}))
		.pipe(gulp.dest('dist'));
});
```


## API

### debug(options)

#### options

##### title

Type: `string`  
Default: ''

Give it a title so it's possible to distinguish the output of multiple instances logging at once.

##### verbose

Type: `boolean`  
Default: `false`

Show more debugging:

- the file [stat object](http://nodejs.org/api/fs.html#fs_class_fs_stats)
- shows more of the contents, from 40 bytes to 400 bytes


## License

MIT © [Sindre Sorhus](http://sindresorhus.com)
