module.exports = function(js) {
    var TokenType = require('../token-types');

    var tokens = [],
        urlMode = false,
        blockMode = 0,
        c, // current character
        cn, // next character
        pos = 0,
        tn = 0,
        ln = 1;

    var Punctuation = {
        ' ': TokenType.Space,
        '\n': TokenType.Newline,
        '\r': TokenType.Newline,
        '\t': TokenType.Tab,
        '!': TokenType.ExclamationMark,
        '"': TokenType.QuotationMark,
        '#': TokenType.NumberSign,
        '$': TokenType.DollarSign,
        '%': TokenType.PercentSign,
        '&': TokenType.Ampersand,
        '\'': TokenType.Apostrophe,
        '(': TokenType.LeftParenthesis,
        ')': TokenType.RightParenthesis,
        '*': TokenType.Asterisk,
        '+': TokenType.PlusSign,
        ',': TokenType.Comma,
        '-': TokenType.HyphenMinus,
        '.': TokenType.FullStop,
        '/': TokenType.Solidus,
        ':': TokenType.Colon,
        ';': TokenType.Semicolon,
        '<': TokenType.LessThanSign,
        '=': TokenType.EqualsSign,
        '>': TokenType.GreaterThanSign,
        '?': TokenType.QuestionMark,
        '@': TokenType.CommercialAt,
        '[': TokenType.LeftSquareBracket,
        ']': TokenType.RightSquareBracket,
        '^': TokenType.CircumflexAccent,
        '_': TokenType.LowLine,
        '{': TokenType.LeftCurlyBracket,
        '|': TokenType.VerticalLine,
        '}': TokenType.RightCurlyBracket,
        '~': TokenType.Tilde
    };

    /**
     * Add a token to the token list
     * @param {string} type
     * @param {string} value
     */
    function pushToken(type, value) {
        tokens.push({ tn: tn++, ln: ln, type: type, value: value });
    }

    /**
     * Check if a character is a decimal digit
     * @param {string} c Character
     * @returns {boolean}
     */
    function isDecimalDigit(c) {
        return '0123456789'.indexOf(c) >= 0;
    }

    /**
     * Parse spaces
     * @param {string} js Unparsed part of js string
     */
    function parseSpaces(js) {
        var start = pos;

        // Read the string until we meet a non-space character:
        for (; pos < js.length; pos++) {
            if (js.charAt(pos) !== ' ') break;
        }

        // Add a substring containing only spaces to tokens:
        pushToken(TokenType.Space, js.substring(start, pos));
        pos--;
    }

    /**
     * Parse a string within quotes
     * @param {string} js Unparsed part of js string
     * @param {string} q Quote (either `'` or `"`)
     */
    function parseString(js, q) {
        var start = pos;

        // Read the string until we meet a matching quote:
        for (pos = pos + 1; pos < js.length; pos++) {
            // Skip escaped quotes:
            if (js.charAt(pos) === '\\') pos++;
            else if (js.charAt(pos) === q) break;
        }

        // Add the string (including quotes) to tokens:
        pushToken(q === '"' ? TokenType.StringDQ : TokenType.StringSQ, js.substring(start, pos + 1));
    }

    /**
     * Parse numbers
     * @param {string} js Unparsed part of js string
     */
    function parseDecimalNumber(js) {
        var start = pos;

        // Read the string until we meet a character that's not a digit:
        for (; pos < js.length; pos++) {
            if (!isDecimalDigit(js.charAt(pos))) break;
        }

        // Add the number to tokens:
        pushToken(TokenType.DecimalNumber, js.substring(start, pos));
        pos--;
    }

    /**
     * Parse identifier
     * @param {string} js Unparsed part of js string
     */
    function parseIdentifier(js) {
        var start = pos;

        // Skip all opening slashes:
        while (js.charAt(pos) === '/') pos++;

        // Read the string until we meet a punctuation mark:
        for (; pos < js.length; pos++) {
            // Skip all '\':
            if (js.charAt(pos) === '\\') pos++;
            else if (js.charAt(pos) in Punctuation) break;
        }

        var ident = js.substring(start, pos);

        // Enter url mode if parsed substring is `url`:
        urlMode = urlMode || ident === 'url';

        // Add identifier to tokens:
        pushToken(TokenType.Identifier, ident);
        pos--;
    }

    /**
    * Parse a multiline comment
    * @param {string} js Unparsed part of js string
    */
    function parseMLComment(js) {
       var start = pos;

       // Read the string until we meet `*/`.
       // Since we already know first 2 characters (`/*`), start reading
       // from `pos + 2`:
       for (pos = pos + 2; pos < js.length; pos++) {
           if (js.charAt(pos) === '*' && js.charAt(pos + 1) === '/') {
               pos++;
               break;
           }
       }

       // Add full comment (including `/*` and `*/`) to the list of tokens:
       pushToken(TokenType.CommentML, js.substring(start, pos + 1));
    }

    function parseSLComment(js) {
       var start = pos;

       // Read the string until we meet line break.
       // Since we already know first 2 characters (`//`), start reading
       // from `pos + 2`:
       for (pos = pos + 2; pos < js.length; pos++) {
           if (js.charAt(pos) === '\n' || js.charAt(pos) === '\r') {
               break;
           }
       }

       // Add comment (including `//` and line break) to the list of tokens:
       pushToken(TokenType.CommentSL, js.substring(start, pos));
       pos--;
    }

    /**
     * Convert a js string to a list of tokens
     * @param {string} js js string
     * @returns {Array} List of tokens
     * @private
     */
    function getTokens(js) {
        // Parse string, character by character:
        for (pos = 0; pos < js.length; pos++) {
            c = js.charAt(pos);
            cn = js.charAt(pos + 1);

            // If we meet `/*`, it's a start of a multiline comment.
            // Parse following characters as a multiline comment:
            if (c === '/' && cn === '*') {
                parseMLComment(js);
            }

            // If we meet `//` and it is not a part of url:
            else if (!urlMode && c === '/' && cn === '/') {
                // If we're currently inside a block, treat `//` as a start
                // of identifier. Else treat `//` as a start of a single-line
                // comment:
                if (blockMode > 0) parseIdentifier(js);
                else parseSLComment(js);
            }

            // If current character is a double or single quote, it's a start
            // of a string:
            else if (c === '"' || c === "'") {
                parseString(js, c);
            }

            // If current character is a space:
            else if (c === ' ') {
                parseSpaces(js)
            }

            // If current character is a punctuation mark:
            else if (c in Punctuation) {
                // Add it to the list of tokens:
                pushToken(Punctuation[c], c);
                if (c === '\n' || c === '\r') ln++; // Go to next line
                if (c === ')') urlMode = false; // exit url mode
                if (c === '{') blockMode++; // enter a block
                if (c === '}') blockMode--; // exit a block
            }

            // If current character is a decimal digit:
            else if (isDecimalDigit(c)) {
                parseDecimalNumber(js);
            }

            // If current character is anything else:
            else {
                parseIdentifier(js);
            }
        }

        return tokens;
    }

    return getTokens(js);
};
