var gutil = require('gulp-util');
var exec = require('child_process').exec;

module.exports = function (opt, cb) {
  if (!cb && typeof opt === 'function') {
    // optional options
    cb = opt;
    opt = {};
  }
  if(!opt) opt = { };
  if(!opt.log) opt.log = !cb;
  if(!opt.cwd) opt.cwd = process.cwd();
  if(!opt.args) opt.args = ' ';

  var cmd = "git " + opt.args;
  return exec(cmd, {cwd : opt.cwd}, function(err, stdout, stderr){
    if(err) return cb(err, stderr);
    if(opt.log) gutil.log(cmd + '\n' + stdout, stderr);
    else gutil.log(cmd + ' (log : false)', stderr);
    cb(err, stdout);
  });
};
