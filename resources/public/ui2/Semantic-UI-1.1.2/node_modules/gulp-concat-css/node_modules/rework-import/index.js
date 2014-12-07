'use strict';

var css = require('css');
var findFile = require('find-file');
var fs = require('fs');
var parseImport = require('parse-import');
var path = require('path');

/**
 * Inline stylesheet using `@import`
 *
 * @param {Object} style
 * @param {Object} opts
 * @api public
 */

function Import(style, opts) {
    var sourceDir
    if (style.rules.length && style.rules[0].position && style.rules[0].position.source) {
        sourceDir = path.dirname(style.rules[0].position.source)
    }

    this.opts = opts || {};

    this.opts.path = (
        // convert string to an array or single element
        typeof this.opts.path === 'string' ?
        [this.opts.path] :
        (this.opts.path || []) // fallback to empty array
    );
    // if source available, prepend sourceDir in the path array
    if (sourceDir && this.opts.path.indexOf(sourceDir) === -1) {
        this.opts.path.unshift(sourceDir);
    }
    // if we got nothing for the path, just use cwd
    if (this.opts.path.length === 0) {
        this.opts.path.push(process.cwd());
    }
    this.opts.transform = this.opts.transform || function(value) { return value };
    this.rules = style.rules || [];
}

/**
 * Process stylesheet
 *
 * @api public
 */

Import.prototype.process = function () {
    var rules = [];
    var self = this;

    this.rules.forEach(function (rule) {
        if (rule.type !== 'import') {
            return rules.push(rule);
        }

        var data = parseImport(rule.import);

        // ignore protocol base uri (protocol://url) or protocol-relative (//url)
        if (data.path.match(/^(?:[a-z]+:)?\/\//i)) {
            return rules.push(rule);
        }

        var opts = cloneOpts(self.opts);
        opts.source = self._check(data.path, rule.position ? rule.position.source : undefined);
        var dirname = path.dirname(opts.source);

        if (opts.path.indexOf(dirname) === -1 ) {
            opts.path = opts.path.slice();
            opts.path.unshift(dirname);
        }

        var media = data.condition;
        var res;
        var content = self._read(opts.source);

        parseStyle(content, opts);

        if (!media || !media.length) {
            res = content.rules;
        } else {
            res = {
                type: 'media',
                media: media,
                rules: content.rules
            };
        }

        rules = rules.concat(res);
    });

    return rules;
};

/**
 * Read the contents of a file
 *
 * @param {String} file
 * @api private
 */

Import.prototype._read = function (file) {
    var data = this.opts.transform(fs.readFileSync(file, this.opts.encoding || 'utf8'), file);
    var style = css.parse(data, {source: file}).stylesheet;

    return style;
};

/**
 * Check if a file exists
 *
 * @param {String} name
 * @api private
 */

Import.prototype._check = function (name, source) {
    var file = findFile(name, { path: this.opts.path, global: false });
    if (!file) {
        throw new Error(
            'Failed to find ' + name +
            (source ? "\n    from " + source : "") +
            "\n    in [ " +
            "\n        " + this.opts.path.join(",\n        ") +
            "\n    ]"
        );
    }

    return file[0];
};

/**
 * Parse @import in given style
 *
 * @param {Object} style
 * @param {Object} opts
 */

function parseStyle(style, opts) {
    var inline = new Import(style, opts);
    var rules = inline.process();

    style.rules = rules;
}

/**
 * Clone object
 *
 * @param {Object} obj
 */

function cloneOpts(obj) {
    var opts = {};
    opts.path = obj.path.slice();
    opts.source = obj.source;
    opts.transform = obj.transform;
    return opts;
}

/**
 * Module exports
 */

module.exports = function (opts) {
    return function (style) {
        parseStyle(style, opts);
    };
};
