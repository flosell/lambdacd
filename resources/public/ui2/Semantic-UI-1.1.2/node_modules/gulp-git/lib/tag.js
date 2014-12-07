var gutil = require('gulp-util');
var exec = require('child_process').exec;
var escape = require('any-shell-escape');

module.exports = function (version, message, opt, cb) {
  if (!cb && typeof opt === 'function') {
    // optional options
    cb = opt;
    opt = {};
  }
  if(!version) return cb(new Error('Version must be defined'));
  if(!message) return cb(new Error('Message must be defined'));
  if(!opt) opt = {};
  if(!opt.cwd) opt.cwd = process.cwd();
  if(!opt.args) opt.args = ' ';

  var signedarg = opt.signed ? ' -s ' : ' -a ';

  var cmd = 'git tag ' + signedarg + ' -m "' + message + '" ' + opt.args + ' ' + escape([version]);
  var templ = gutil.template(cmd, {file:message});
  return exec(templ, {cwd: opt.cwd}, function(err, stdout, stderr){
    if(err) return cb(err);
    gutil.log(stdout, stderr);
    cb();
  });
};
