module.exports = (function() {
    function throwError(e, src) {
        var line = e.line;
        var name = 'Parsing error';
        var version = require('../package.json').version;
        var message = formatErrorMessage(src, line, version);
        var error = {
            line: line,
            syntax: e.syntax,
            version: version,
            name: name,
            message: message
        }
        error.toString = function() {return this.name + ': ' + this.message;};
        throw error;
    }

    function formatErrorMessage(text, ln, version) {
        var message = ['Please check the validity of the block starting from line #' + ln];

        message.push('');
        var code = formatCodeFragment(text, ln);
        message = message.concat(code);
        message.push('');

        message.push('Gonzales PE version: ' + version);

        return message.join('\n');
    }

    function formatCodeFragment(text, lineNumber) {
        var lines = text.split(/\r\n|\r|\n/);
        var linesAround = 2;
        var result = [];

        for (var i = lineNumber - 1 - linesAround; i < lineNumber + linesAround; i++) {
            var line = lines[i];
            if (!line) continue;
            var ln = i + 1;
            var mark = ln === lineNumber ? '*' : ' ';
            result.push(ln + mark + '| ' + line);
        }

        return result;
    }

    return function(options) {
        var src, rule, syntax, getTokens, mark, rules, tokens, ast;

        if (!options || !options.src) throw new Error('Please, pass a string to parse');

        src = typeof options === 'string' ? options : options.src;
        syntax = options.syntax || 'css';
        rule = options.rule || (syntax === 'js' ? 'program' : 'stylesheet');

        var fs = require('fs');
        if (!fs.existsSync(__dirname + '/' + syntax))
            return console.error('Syntax "' + syntax + '" is not supported yet, sorry');

        getTokens = require('./' + syntax + '/tokenizer');
        mark = require('./' + syntax + '/mark');
        rules = require('./' + syntax + '/rules');

        tokens = getTokens(src);
        mark(tokens);

        try {
            ast = rules(tokens, rule);
        } catch (e) {
            throwError(e, src);
        }

        return ast;
    }
})();
