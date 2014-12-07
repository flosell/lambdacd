module.exports = function stringify(tree) {
    // TODO: Better error message
    if (!tree) throw new Error('We need tree to translate');

    var _m_simple = {
            'attrselector': 1, 'combinator': 1, 'nth': 1, 'number': 1,
            'operator': 1, 'raw': 1, 's': 1, 'string': 1, 'unary': 1
        },
        _m_composite = {
            'atruleb': 1, 'atrulerq': 1, 'atrulers': 1, 'atrules': 1,'condition': 1,
            'conditionalStatement': 1,
            'declaration': 1, 'dimension': 1, 'filterv': 1, 'function': 1,
            'ident': 1, 'include': 1,
            'loop': 1, 'mixin': 1, 'selector': 1, 'progid': 1, 'property': 1,
            'ruleset': 1, 'simpleselector': 1, 'stylesheet': 1, 'value': 1
        },
        _m_primitive = {
            'declDelim': '\n', 'delim': ',',
            'namespace': '|', 'parentselector': '&', 'propertyDelim' : ':'
        };

    function _t(tree) {
        var t = tree[0];
        if (t in _m_primitive) return _m_primitive[t];
        else if (t in _m_simple) return _simple(tree);
        else if (t in _m_composite) return _composite(tree);
        return _unique[t](tree);
    }

    function _composite(t, i) {
        var s = '';
        i = i === undefined ? 1 : i;
        for (; i < t.length; i++) s += typeof t[i] === 'string' ? t[i] : _t(t[i]);
        return s;
    }

    function _simple(t) {
        return t[1];
    }

    var _unique = {
        'arguments': function(t) {
            return '(' + _composite(t) + ')';
        },
        'atkeyword': function(t) {
            return '@' + _t(t[1]);
        },
        'atruler': function(t) {
            return _t(t[1]) + _t(t[2]) + '{' + _t(t[3]) + '}';
        },
        'attrib': function(t) {
            return '[' + _composite(t) + ']';
        },
        'block': function(t) {
            return _composite(t);
        },
        'braces': function(t) {
            return t[1] + _composite(t, 3) + t[2];
        },
        'class': function(t) {
            return '.' + _t(t[1]);
        },
        'commentML': function (t) {
            return '/*' + t[1];
        },
        'commentSL': function (t) {
            return '/' + '/' + t[1];
        },
        'default': function(t) {
            return '!' + _composite(t) + 'default';
        },
        'filter': function(t) {
            return _t(t[1]) + ':' + _t(t[2]);
        },
        'functionExpression': function(t) {
            return 'expression(' + t[1] + ')';
        },
        'important': function(t) {
            return '!' + _composite(t) + 'important';
        },
        'interpolation': function(t) {
            return '#{' + _t(t[1]) + '}';
        },
        'nthselector': function(t) {
            return ':' + _simple(t[1]) + '(' + _composite(t, 2) + ')';
        },
        'percentage': function(t) {
            return _t(t[1]) + '%';
        },
        'placeholder': function(t) {
            return '%' + _t(t[1]);
        },
        'pseudoc': function(t) {
            return ':' + _t(t[1]);
        },
        'pseudoe': function(t) {
            return '::' + _t(t[1]);
        },
        'shash': function (t) {
            return '#' + t[1];
        },
        'uri': function(t) {
            return 'url(' + _composite(t) + ')';
        },
        'variable': function(t) {
            return '$' + _t(t[1]);
        },
        'variableslist': function(t) {
            return _t(t[1]) + '...';
        },
        'vhash': function(t) {
            return '#' + t[1];
        }
    };

    return _t(tree);
}
