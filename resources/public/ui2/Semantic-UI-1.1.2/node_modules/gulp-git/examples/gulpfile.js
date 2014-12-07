var gulp = require('gulp');
var git = require('../');



// Init a git repo

gulp.task('init', function(){
  git.init();
});


// Add files

gulp.task('add', function(){
  gulp.src('./*')
  .pipe(git.add());
});


// Commit files

gulp.task('commit', function(){
  gulp.src('./*', {buffer:false})
  .pipe(git.commit('initial commit'));
});

// Commit files with arguments
gulp.task('commitopts', function(){
  gulp.src('./*')
  .pipe(git.commit('initial commit', {args: "-v"}));
});


// Add remote

gulp.task('remote', function(){
  git.addRemote('origin', 'https://github.com/stevelacy/git-test', function (err) {
    //if (err) ...
  });
});


// Push to remote repo

gulp.task('push', function(){
  git.push('origin', 'master', function (err) {
    //if (err) ...
  });
});


// Pull from remote repo

gulp.task('pull', function(){
  git.pull('origin', 'master', function (err) {
    //if (err) ...
  });
});

// Tag the repo

gulp.task('tag', function(){
  git.tag('v1.1.1', 'Version message', function (err) {
    //if (err) ...
  });
});

// Tag the repo WITH signed key
gulp.task('tagsec', function(){
  git.tag('v1.1.1', 'Version message with signed key', {signed:true}, function (err) {
    //if (err) ...
  });
});

gulp.task('push-tag', function(){
  git.push('origin', 'v1.1.1', function (err) {
    //if (err) ...
  });
});


gulp.task('rm', function(){
  gulp.src('./delete')
  .pipe(git.rm({args: '-f'}));
});

gulp.task('addSubmodule', function(){
  git.addSubmodule('https://github.com/stevelacy/git-test', 'git-test', {args: '-b master'});
});

gulp.task('updateSubmodules', function(){
  git.updateSubmodule({args: '--init'});
});

// default gulp task

gulp.task('default', ['add']);
