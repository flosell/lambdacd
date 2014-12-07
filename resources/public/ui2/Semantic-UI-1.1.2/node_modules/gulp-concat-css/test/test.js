var expect = require('chai').expect,
  through = require('through2'),
  gutil = require('gulp-util'),
  fs = require('fs'),
  path = require('path'),
  concatCss = require('../');


function expected(file) {
  var base = path.join(process.cwd(), 'test/expected');
  var filepath = path.resolve(base, file);
  return new gutil.File({
    path: filepath,
    cwd: process.cwd(),
    base: base,
    contents: fs.readFileSync(filepath)
  });
}

function fixture(file) {
  var base = path.join(process.cwd(), 'test/fixtures');
  var filepath = path.join(base, file);
  return new gutil.File({
    path: filepath,
    cwd: process.cwd(),
    base: base,
    contents: fs.readFileSync(filepath)
  });
}

describe('gulp-concat-css', function() {
  it('should concat, rebase urls and inline imports', function(done) {

    var stream = concatCss('build/bundle.css');
    var expectedFile = expected('build/bundle.css');
    stream
      .pipe(through.obj(function(file, enc, cb) {
        //fs.writeFileSync("bundle.css", file.contents);
        expect(String(file.contents)).to.be.equal(String(expectedFile.contents));
        expect(path.basename(file.path)).to.be.equal(path.basename(expectedFile.path));
        expect(file.cwd, "cwd").to.be.equal(expectedFile.cwd);
        expect(file.relative, "relative").to.be.equal(expectedFile.relative);
        done();
      }));

    stream.write(fixture('main.css'));
    stream.write(fixture('vendor/vendor.css'));
    stream.end();
  });
});
