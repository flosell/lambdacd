'use strict';

var importRegex = require('import-regex');

/**
 * Trim string
 *
 * @param {String} str
 * @api private
 */

function trim(str) {
    str = str
        .replace(/(^|\s)@import(\s|$)/, '')
        .replace(/(^|\s)url\s?\(/, '')
        .replace(/\)(\s|$)/, '')
        .replace(/(^|\s)("|\')/, '')
        .replace(/("|\')(\s|$)/, '');

    return str;
}

/**
 * Get @import statements from a string
 *
 * @param {String} str
 * @api public
 */

module.exports = function (str) {
    var ret = {};

    if (!str.match(importRegex())) {
        throw new Error('Could not find a valid import path in string: ' + str);
    }

    ret.path = trim(str.match(importRegex()).toString().trim());
    ret.condition = str.replace(importRegex(), '').replace(' ', '').trim();
    ret.rule = str.trim();

    return ret;
};
