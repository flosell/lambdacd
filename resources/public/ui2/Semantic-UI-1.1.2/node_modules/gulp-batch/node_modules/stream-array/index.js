var Readable = require('readable-stream').Readable
    , Queue = require('fastqueue')
;

function StreamArray(list) {
    if (!Array.isArray(list))
        throw new TypeError('First argument must be an Array');

    Readable.call(this, {objectMode:true});

    this._queue = new Queue();
    this._queue.tail = list;
    this._queue.length = list.length;
}

StreamArray.prototype = Object.create(Readable.prototype, {constructor: {value: StreamArray}});

StreamArray.prototype._read = function(size) {
    this.push(this._queue.length ? this._queue.shift() : null);
};

module.exports = function(list) {
    return new StreamArray(list);
};
