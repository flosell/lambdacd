var TokenType = require('../token-types');
var NodeType = require('./node-types');

module.exports = (function() {
    var tokens, tokensLength, pos;

    var rules = {
        'functionBody': function() { return checkFunctionBody(pos) && getFunctionBody(); },
        'functionDeclaration': function() { return checkFunctionDeclaration(pos) && getFunctionDeclaration(); },
        'papams': function() { return checkParams(pos) && getParams(); },
        'program': function() { return checkProgram(pos) && getProgram(); },
        'space': function() { return checkSpace(pos) && getSpace(); },
        'text': function() { return checkText(pos) && getText(); }
    };

    /**
     * Stop parsing and display error
     * @param {Number=} i Token's index number
     */
    function throwError(i) {
        var ln = i ? tokens[i].ln : tokens[pos].ln;

        throw {line: ln, syntax: 'js'};
    }

    /**
     * @param {Object} exclude
     * @param {Number} i Token's index number
     * @returns {Number}
     */
    function checkExcluding(exclude, i) {
        var start = i;

        while(i < tokensLength) {
            if (exclude[tokens[i++].type]) break;
        }

        return i - start - 2;
    }

    /**
     * @param {Number} start
     * @param {Number} finish
     * @returns {String}
     */
    function joinValues(start, finish) {
        var s = '';

        for (var i = start; i < finish + 1; i++) {
            s += tokens[i].value;
        }

        return s;
    }

    /**
     * @param {Number} start
     * @param {Number} num
     * @returns {String}
     */
    function joinValues2(start, num) {
        if (start + num - 1 >= tokensLength) return;

        var s = '';

        for (var i = 0; i < num; i++) {
            s += tokens[start + i].value;
        }

        return s;
    }


/////////////////////////////////////
/////////////////////////////////////
/////////////////////////////////////

    function checkFunctionBody(i) {
        var start = i;

        if (i >= tokensLength) return 0;

        if (tokens[i].type === TokenType.LeftCurlyBracket) i = tokens[i].right + 1;
        else return 0;

        return i - start;
    }

    function getFunctionBody() {
        var startPos = pos,
            x = [NodeType.FUNCTION_BODY];

        // Skip `{`:
        pos++;

        x.push(joinValues(pos, tokens[pos].right - 1));

        // Skip `}`:
        pos++;

        return x;
    }

    /**
     * @param {Number} i Token's index number
     * @returns {Number}
     */
    function checkFunctionDeclaration(i) {
        var start = i,
            l;

        if (i >= tokensLength) return 0;

        if (tokens[i].value === 'function') i++;
        else return 0;

        if (l = checkSpace(i)) i += l;
        else return 0;

        if (tokens[i].type === TokenType.Identifier) i++;
        else return 0;

        if (l = checkSpace(i)) i += l;

        if (l = checkParams(i)) i += l;
        else return 0;

        if (l = checkSpace(i)) i += l;

        if (l = checkFunctionBody(i)) i += l;
        else return 0;

        return i - start;
    }

    /**
     * @returns {Array}
     */
    function getFunctionDeclaration() {
        var startPos = pos,
            x = [NodeType.FUNCTION_DECLARATION];

        // Skip `function` word:
        pos++;

        x.push(getSpace());

        // TODO: `getIdent`:
        x.push(['ident', tokens[pos].value]);
        pos++;

        if (checkSpace(pos)) x.push(getSpace());

        x.push(getParams());

        if (checkSpace(pos)) x.push(getSpace());

        x.push(getFunctionBody());

        return x;
    }

    function checkParams(i) {
        var start = i;

        if (tokens[i].type === TokenType.LeftParenthesis) i = tokens[i].right + 1;
        else return 0;

        return i - start;
    }

    function getParams() {
        var startPos = pos,
            x = [NodeType.PARAMS];

        // Skip `(`:
        pos++;

        x.push(joinValues(pos, tokens[pos].right - 1));

        // Skip `)`:
        pos++;

        return x;
    }

    /**
     * @param {Number} i Token's index number
     * @returns {Number}
     */
    function checkProgram(i) {
        var start = i,
            l;

        while (i < tokensLength) {
            if (l = checkFunctionDeclaration(i) || checkText(i)) i += l;
            else break;
        }

        return i - start;
    }

    /**
     * @returns {Array}
     */
    function getProgram() {
        var startPos = pos,
            x = [NodeType.PROGRAM];

        while (pos < tokensLength) {
            if (checkFunctionDeclaration(pos)) x.push(getFunctionDeclaration());
            else if (checkText(pos)) x.push(getText());
            else throwError();
        }

        return x;
    }

    /**
     * Check if token is marked as a space (if it's a space or a tab
     *      or a line break).
     * @param i
     * @returns {Number} Number of spaces in a row starting with the given token.
     */
    function checkSpace(i) {
        return i < tokensLength && tokens[i].ws ?
               tokens[i].ws_last - i + 1 :
               0;
    }

    /**
     * Get node with spaces
     * @returns {Array} `['s', x]` where `x` is a string containing spaces
     */
    function getSpace() {
        var startPos = pos,
            x = [NodeType.SPACE, joinValues(pos, tokens[pos].ws_last)];

        pos = tokens[pos].ws_last + 1;

        return x;
    }


    /**
     * @param {Number} i Token's index number
     * @returns {Number}
     */
    function checkText(i) {
        var start = i,
            l;

        if (i >= tokensLength) return 0;

        while (i < tokensLength && !checkFunctionDeclaration(i)) i++;

        return i - start;
    }

    /**
     * @returns {Array}
     */
    function getText() {
        var startPos = pos,
            x = [NodeType.TEXT];

        while (pos < tokensLength && !checkFunctionDeclaration(pos)) pos++;

        x.push(joinValues(startPos, pos - 1));

        return x;
    }

    return function(_tokens, rule) {
        tokens = _tokens;
        tokensLength = tokens.length;
        pos = 0;

        return rules[rule]();
   };
})();
