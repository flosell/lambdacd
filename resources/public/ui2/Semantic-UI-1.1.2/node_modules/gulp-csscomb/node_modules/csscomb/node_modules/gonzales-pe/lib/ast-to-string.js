module.exports = (function() {
    function dummySpaces(num) {
        return '                                                  '
            .substr(0, num * 2);
    }

    return function astToString(tree, level) {
        level = level || 0;
        var spaces, string;

        if (level) {
            spaces = dummySpaces(level);
            string = '\n' + spaces + '[';
        } else {
            string = '[';
        }

        tree.forEach(function(node) {
            if (typeof node.ln !== 'undefined') return;
            string += Array.isArray(node) ?
                astToString(node, level + 1) :
                ('\'' + node.toString() + '\'');
            string += ', ';
        });

        return string.substr(0, string.length - 2) + ']';
    };
})();
