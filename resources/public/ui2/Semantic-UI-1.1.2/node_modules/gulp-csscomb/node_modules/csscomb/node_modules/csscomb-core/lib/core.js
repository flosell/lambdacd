var gonzales = require('gonzales-pe');
var minimatch = require('minimatch');
var vow = require('vow');
var vfs = require('vow-fs');

/**
 * @param {Array} predefinedOptions
 * @constructor
 * @name Comb
 */
var Comb = function(predefinedOptions) {
    var _this = this;
    // List of supported syntaxes:
    var supportedSyntaxes = Array.prototype.slice.call(arguments, 1);
    // List of file paths that should be excluded from processing:
    var exclude;
    // List of configured options with values:
    var configuredOptions;
    // List of handlers:
    var handlers;
    // Whether lint mode is on:
    var lint;
    // Whether verbose mode is on:
    var verbose;
    // Map of used options:
    var options = {};
    // List of option names in exact order they should be processed:
    var optionsOrder = [];
    var unmetOptions = {};

    /**
     * PRIVATE METHODS
     */

    function usePredefinedOptions() {
        if (!predefinedOptions) return;

        predefinedOptions.forEach(function(option) {
            _this.use(option);
        });
    }

    /**
     * @param {Object} option
     */
    function updateOptionOrder(option) {
        var name = option.name;
        var runBefore = option.runBefore;
        var runBeforeIndex;

        options[name] = option;

        if (runBefore) {
            runBeforeIndex = optionsOrder.indexOf(runBefore);
            if (runBeforeIndex > -1) {
                optionsOrder.splice(runBeforeIndex, 0, name);
            } else {
                optionsOrder.push(name);
                if (!unmetOptions[runBefore]) unmetOptions[runBefore] = [];
                unmetOptions[runBefore].push(name);
            }
        } else {
            optionsOrder.push(name);
        }

        var unmet = unmetOptions[name];
        if (unmet) {
            unmet.forEach(function(name) {
                var i = optionsOrder.indexOf(name);
                optionsOrder.splice(i, 1);
                optionsOrder.splice( -1, 0, name);
            });
        }
    }

    /**
     * Adds an option to list of configured options.
     *
     * @param {Object} option Option's object
     * @param {Object} value Value that should be set for the option
     * @returns {Object} Object with option's name, value and link to
     * `process()` method
     */
    function addHandler(option, value) {
        value = option.setValue ?
            option.setValue(value) :
            setValue(option.accepts, value);
        handlers.push(option);
        configuredOptions[option.name] = value;
    }

    /**
     * Processes value and checks if it is acceptable by the option.
     *
     * @param {Object} acceptableValues Map of value types that are acceptable
     * by option. If `string` property is present, its value is a regular
     * expression that is used to validate value passed to the function.
     * @param {Object|undefined} value
     * @returns {Boolean|String} Valid option's value
     */
    function setValue(acceptableValues, value) {
        if (!acceptableValues) throw new Error('Option\'s module must either' +
            ' implement `setValue()` method or provide `accepts` object' +
            ' with acceptable values.');

        var valueType = typeof value;
        var pattern = acceptableValues[valueType];

        if (!pattern) throw new Error('The option does not accept values of type "' +
            valueType + '".\nValue\'s type must be one the following: ' +
            Object.keys(acceptableValues).join(', ') + '.');

        switch (valueType) {
            case 'boolean':
                if (pattern.indexOf(value) < 0) throw new Error(' Value must be ' +
                    'one of the following: ' + pattern.join(', ') + '.');
                return value;
            case 'number':
                if (value !== parseInt(value)) throw new Error('Value must be an integer.');
                return new Array(value + 1).join(' ');
            case 'string':
                if (!value.match(pattern)) throw new Error('Value must match pattern ' +
                    pattern + '.');
                return value;
            default:
                throw new Error('If you see this message and you are not' +
                    ' a developer adding a new option, please open an issue here:' +
                    ' https://github.com/csscomb/csscomb.js/issues/new' +
                    '\nFor option to accept values of type "' + valueType +
                    '" you need to implement custom `setValue()` method. See' +
                    ' `lib/options/sort-order.js` for example.');
        }
    }

    /**
     * Checks if path is present in `exclude` list.
     *
     * @param {String} path
     * @returns {Boolean} False if specified path is present in `exclude` list.
     * Otherwise returns true.
     */
    function shouldProcess(path) {
        path = path.replace(/^\.\//, '');
        for (var i = exclude.length; i--;) {
            if (exclude[i].match(path)) return false;
        }
        return true;
    }

    /**
     * Checks if specified path is not present in `exclude` list and it has one of
     * acceptable extensions.
     *
     * @param {String} path
     * @returns {Boolean} False if the path either has unacceptable extension or
     * is present in `exclude` list. True if everything is ok.
     */
    function shouldProcessFile(path) {
        // Get file's extension:
        var syntax = path.split('.').pop();

        // Check if syntax is supported. If not, ignore the file:
        if (supportedSyntaxes.indexOf(syntax) < 0) {
            return false;
        }
        return shouldProcess(path);
    }

    /**
     * Processes stylesheet tree node.
     *
     * @param {Array} tree Parsed tree
     * @returns {Array} Modified tree
     */
    function processTree(tree) {
        var _handlers;

        _handlers = handlers.filter(function(handler) {
            var syntax = _this.getSyntax();
            return handler.syntax.indexOf(syntax) > -1;
        }).map(function(handler) {
            return handler.process;
        });

        // We walk across complete tree for each handler,
        // because we need strictly maintain order in which handlers work,
        // despite fact that handlers work on different level of the tree.
        _handlers.forEach(function(handler) {
            processNode(['tree', tree], 0, handler);
        });
        return tree;
    }

    /**
     * Processes tree node.
     *
     * @param {Array} node Tree node
     * @param {Number} level Indent level
     * @param {Object} handler Option's handler
     */
    function processNode(node, level, handler) {
        node.forEach(function(node) {
            if (!Array.isArray(node)) return;

            var nodeType = node.shift();
            handler.call(_this, nodeType, node, level);
            node.unshift(nodeType);

            if (nodeType === 'atrulers' || nodeType === 'block') level++;

            processNode(node, level, handler);
        });
    }

    /**
     * PUBLIC INSTANCE METHODS
     * Methods that depend on certain instance variables, e.g. configuration:
     *   - use;
     *   - configure;
     *   - getOptionsOrder;
     *   - getValue;
     *   - getSyntax;
     *   - processPath;
     *   - processDirectory;
     *   - processFile;
     *   - processString;
     */

    /**
     *
     * @param {Object} option
     * @returns {Object} Comb's object
     */
    this.use = function use(option) {
        var name;

        if (typeof option !== 'object') {
            throw new Error('Can\'t use option because it is not an object');
        }

        name = option.name;
        if (typeof name !== 'string' || !name) {
            throw new Error('Can\'t use option because it has invalid name: ' +
                            name);
        }

        if (typeof option.accepts !== 'object' &&
            typeof option.setValue !== 'function') {
            throw new Error('Can\'t use option "' + name + '"');
        }

        if (typeof option.process !== 'function') {
            throw new Error('Can\'t use option "' + name + '"');
        }

        updateOptionOrder(option);

        return this;
    };

    /**
     * Loads configuration from JSON.
     * Activates and configures required options.
     *
     * @param {Object} config
     * @returns {Object} Comb's object (that makes the method chainable).
     */
    this.configure = function configure(config) {
        handlers = [];
        configuredOptions = {};
        verbose = config.verbose;
        lint = config.lint;
        exclude = (config.exclude || []).map(function(pattern) {
            return new minimatch.Minimatch(pattern);
        });

        optionsOrder.forEach(function(optionName) {
            if (config[optionName] === undefined) return;

            try {
                addHandler(options[optionName], config[optionName]);
            } catch (e) {
                // Show warnings about illegal config values only in verbose mode:
                if (verbose) {
                    console.warn('\nFailed to configure "%s" option:\n%s',
                                 optionName, e.message);
                }
            }
        });

        return this;
    };

    this.getOptionsOrder = function getOptionsOrder() {
        return optionsOrder.slice();
    };

    /**
     * Gets option's value.
     *
     * @param {String} optionName
     * @returns {String|Boolean|undefined}
     */
    this.getValue = function getValue(optionName) {
        return configuredOptions[optionName];
    };

    this.getSyntax = function getSyntax() {
        return _this.syntax;
    };

    /**
     * Processes directory or file.
     *
     * @param {String} path
     * @returns {Promise}
     */
    this.processPath = function processPath(path) {
        path = path.replace(/\/$/, '');

        return vfs.exists(path).then(function(exists) {
            if (!exists) {
                console.warn('Path ' + path + ' was not found.');
                return;
            }
            return vfs.stat(path).then(function(stat) {
                if (stat.isDirectory()) {
                    return _this.processDirectory(path);
                } else {
                    return _this.processFile(path);
                }
            });
        });
    };

    /**
     * Processes directory recursively.
     *
     * @param {String} path
     * @returns {Promise}
     */
    this.processDirectory = function processDirectory(path) {
        return vfs.listDir(path).then(function(filenames) {
            return vow.all(filenames.map(function(filename) {
                var fullname = path + '/' + filename;
                return vfs.stat(fullname).then(function(stat) {
                    if (stat.isDirectory()) {
                        return shouldProcess(fullname) && _this.processDirectory(fullname);
                    } else {
                        return _this.processFile(fullname);
                    }
                });
            })).then(function(results) {
                return [].concat.apply([], results);
            });
        });
    };

    /**
     * Processes single file.
     *
     * @param {String} path
     * @returns {Promise}
     */
    this.processFile = function processFile(path) {
        if (!shouldProcessFile(path)) return;
        return vfs.read(path, 'utf8').then(function(data) {
            var syntax = path.split('.').pop();
            var processedData = _this.processString(data, { syntax: syntax, filename: path });
            var isChanged = data !== processedData;

            var tick = isChanged ? (lint ? '!' : '✓') : ' ';
            var output = function() {
                if (verbose) console.log(tick, path);
                return isChanged ? 1 : 0;
            };

            if (!isChanged || lint) {
                return output();
            } else {
                return vfs.write(path, processedData, 'utf8').then(output);
            }
        });
    };

    /**
     * Processes a string.
     *
     * @param {String} text
     * @param {{context: String, filename: String, syntax: String}} options
     * @returns {String} Processed string
     */
    this.processString = function processString(text, options) {
        var syntax = options && options.syntax;
        var filename = options && options.filename || '';
        var context = options && options.context;
        var tree;

        if (!text) return text;

        // TODO: Parse different syntaxes
        _this.syntax = syntax || 'css';

        try {
            tree = gonzales.srcToAST({ src: text, syntax: syntax, rule: context });
        } catch (e) {
            var version = require('../package.json').version;
            var message = [filename,
                           e.message,
                          'CSScomb Core version: ' + version];
            e.stack = e.message = message.join('\n');
            throw e;
        }

        tree = processTree(tree);
        return gonzales.astToSrc({ syntax: syntax, ast: tree });
    };

    usePredefinedOptions();
};

module.exports = Comb;

