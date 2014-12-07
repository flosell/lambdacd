module.exports = function astToSrc(options) {
    var ast, syntax, stringify;

    ast = typeof options === 'string' ? options : options.ast;
    syntax = options.syntax || 'css';

    try {
        stringify = require('./' + syntax + '/stringify');
    } catch (e) {
        return console.error('Syntax "' + syntax + '" is not supported yet, sorry');
    }

    return stringify(ast);
}
