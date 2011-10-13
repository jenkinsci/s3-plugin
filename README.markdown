
Install
=======

Tested with Hudson 1.431

* Upload target/s3.hpi to your instance of Hudson
* Configure S3 profile: Manage Hudson -> Configure System -> Amazon S3 profiles
* Project -> Configure -> [x] Publish artifacts to S3 Bucket

Building
========

You do try either:

1. Within the Hudson tree
  * The plugin originally expected to live in the `hudson/plugins/s3` directory of a Hudson svn checkout
  * While in the `s3` directory, just run `mvn`
2. Standalone tree
  * While in the `hudson-s3` directory, with no parent Hudson source, `mvn` might work
  * Note: you may have to move `dotm2_settings.xml` to `~/.m2/settings.xml`

Notes
=====

* Only the basename of source files is use as the object key name, an option to include the path name relative to the workspace should probably added.

Acknowledgements
================

* The Hudson scp plugin author for providing a great place to start copy/pasting from
* http://github.com/stephenh/hudson-git2 - for this README.markdown template and a great git plugin for hudson
* jets3t - http://jets3t.s3.amazonaws.com/index.html
