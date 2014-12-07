map = require 'map-stream'
{green, magenta} = require 'colors'
path = require 'path'

log = ->
  sig = "[#{green('gulp')}]"
  args = Array.prototype.slice.call arguments
  args.unshift sig

  console.log.apply console, args

print = (options = {}) ->
  options = format: options if typeof options is 'function'
  {format, colors} = options
  colors ?= yes
  format ?= (filepath) -> filepath

  map (file, cb) ->
    filepath = path.relative process.cwd(), file.path
    filepath = magenta(filepath) if colors isnt false
    formatted = format filepath
    print.log formatted if formatted
    cb null, file

print.log = log

module.exports = print
