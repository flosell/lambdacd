var test = require('tape')
    , Queue = require('fastqueue')
;

/**
 * We're using some internals so lets make sure they're not different
 * than when we wrote this.
 */
test('fastqueue', function(t) {
    var q;

    t.ok(Queue, 'fastqueue exists');
    t.equal(typeof(Queue), 'function', 'require returns an object');
    t.equal(0, Object.keys(Queue).length, 'No hidden exports');

    q = new Queue();

    t.equal(typeof(q), 'object', 'new Queue() returns an object');
    t.equal(typeof(q.length), 'number', 'q.length');

    t.ok(Array.isArray(q.head), 'q.head = []');
    t.ok(Array.isArray(q.tail), 'q.tail = []');

    q.push(1);

    t.equal(1, q.length, 'push(length == 1)');
    t.equal(1, q.shift(), 'shift');
    t.equal(0, q.length, 'length == 0');

    t.equal(undefined, q.shift(), 'empty shift === undefined');

    t.end();
});
