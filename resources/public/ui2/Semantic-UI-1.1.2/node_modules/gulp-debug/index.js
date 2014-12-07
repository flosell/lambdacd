'use strict';
var gutil = require('gulp-util');
var through = require('through2');
var tildify = require('tildify');
var dateTime = require('date-time');
var stringifyObject = require('stringify-object');
var chalk = require('chalk');
var prop = chalk.blue;
var header = chalk.underline;

module.exports = function (options) {
	options = options || {};

	var title = options.title ? options.title + ' ' : '';

	return through.obj(function (file, enc, cb) {
		if (file.isStream()) {
			cb(new gutil.PluginError('gulp-debug', 'Streaming not supported'));
			return;
		}

		var trim = function (buf) {
			return buf.toString('utf8', 0, options.verbose ? 1000 : 40).trim() + '...\n';
		}

		var fileObj =
			(file.cwd ? 'cwd:      ' + prop(tildify(file.cwd)) : '') +
			(file.base ? '\nbase:     ' + prop(tildify(file.base)) : '') +
			(file.path ? '\npath:     ' + prop(tildify(file.path)) : '') +
			(file.stat && options.verbose ? '\nstat:     ' + prop(stringifyObject(file.stat)) : '') +
			(file.contents ? '\ncontents: ' + prop(trim(file.contents)) : '');

		gutil.log(
			'gulp-debug: ' + title + chalk.gray('(' + dateTime() + ')') + '\n\n' +
			header('File\n') + fileObj
		);

		cb(null, file);
	}, function (cb) {
		gutil.log('gulp-debug: ' + title + chalk.magenta('end') + ' event fired ' + chalk.gray('(' + dateTime() + ')'));
		cb();
	});
};
