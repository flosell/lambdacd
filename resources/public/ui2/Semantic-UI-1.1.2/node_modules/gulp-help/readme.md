# [gulp](https://github.com/gulpjs/gulp)-help [![NPM version][npm-image]][npm-url] [![Build Status][travis-image]][travis-url]
> Adds a default help task to gulp and provides the ability to add custom help messages to your gulp tasks

## Install

```bash
$ npm install --save-dev gulp-help
```

## Usage

Before defining any tasks, add `gulp help` to your gulp instance

```js
// gulpfile.js
var gulp = require('gulp-help')(require('gulp'));
```

Next, define help text for each custom task

```js
// gulpfile.js
gulp.task('lint', 'Lints all server side js', function () {
    gulp.src('./lib/**/*.js')
      .pipe(jshint());
});
```

Now show that help via `gulp help`

```bash
$ gulp help
[gulp] Running 'help'...

Usage
  gulp [task]

Available tasks
  help Display this help text.
  lint Lints all server side js

[gulp] Finished 'help' in 607 μs
```

## New task API

### gulp.task(name[, help, deps, fn, taskOptions])

#### [name](https://github.com/gulpjs/gulp/blob/master/docs/API.md#name)

Type: `string`

#### help

Type: `string | boolean`

Custom help message as a string.
If you want to hide the task from the help menu, supply `false`.

#### [deps](https://github.com/gulpjs/gulp/blob/master/docs/API.md#deps)

Type: `Array`

#### [fn](https://github.com/gulpjs/gulp/blob/master/docs/API.md#fn)

Type: `function`

#### taskOptions.aliases

Type: `Array`

List of aliases for this task.

## Hide Tasks

You can optionally hide a target from showing up in the help menu by passing `false` as the help argument, e.g.

```js
gulp.task('task-hidden-from-help', false, function () {
  // ...
});
```

However, if the `--all` flag is provided, even these tasks will be shown. (i.e. `gulp help --all`)

## Aliases

You can optionally add aliases to your targets by supplying an object with an aliases array, e.g.

```js
gulp.task('version', 'prints the version.', [], function() {
  // ...
}, {
  aliases: ['v', 'V']
});
```

which results in

```bash
[gulp] Starting 'help'...

Usage
  gulp [task]

Available tasks
  help     Display this help text.
  version  prints the version. Aliases: v, V

[gulp] Finished 'help' after 928 μs
```

## Options

You can optionally pass options to your targets by supplying an object with an options object, e.g.

```js
gulp.task('version', 'prints the version.', [], function () {
  // ...
}, {
  options: {
    'env=prod': 'description of env, perhaps with available values',
    'key=val': 'description of key & val',
    'key': 'description of key'
  }
});
```
which results in

```bash
[gulp] Starting 'help'...

Usage
  gulp [task]

Available tasks
  help          Display this help text.
  version       prints the version.
    --env=prod  description of env, perhaps with available values
    --key=val   description of key & val
    --key       description of key

[gulp] Finished 'help' after 928 μs
```

## Override default help message

```js
require('gulp-help')(gulp, { description: 'you are looking at it.', aliases: ['h', '?'] });
```

Then, calling

```shell
$ gulp      #or
$ gulp help #or
$ gulp h    #or
$ gulp ?
```

will now result in

```
[gulp] Starting 'help'...

Usage:
  gulp [task]

Available tasks:
  help     you are looking at it. Aliases: h, ?

[gulp] Finished 'help' after 1.05 ms
```

## Post-help callback

You can define a function to run after the default help task runs.

```js
require('gulp-help')(gulp, {
  afterPrintCallback: function(tasks) {
    console.log(tasks);
  }
});
```

## License

[MIT](http://opensource.org/licenses/MIT) © [Chris Montgomery](http://www.chrismontgomery.info/)

[npm-url]: https://npmjs.org/package/gulp-help
[npm-image]: http://img.shields.io/npm/v/gulp-help.svg
[travis-image]: https://travis-ci.org/chmontgomery/gulp-help.svg?branch=master
[travis-url]: https://travis-ci.org/chmontgomery/gulp-help
