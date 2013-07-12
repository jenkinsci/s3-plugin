
Install
=======

Tested with Jenkins 1.480

* Upload `target/s3.hpi` to your instance of Jenkins via
./pluginManager/advanced
* Configure S3 profile: Manage Jenkins -> Configure System ->
Amazon S3 profiles
* Project -> Configure -> [x] Publish artifacts to S3 Bucket

Building
========

Just run `mvn`.

Notes
=====

* Only the basename of source files is use as the object key name,
an option to include the path name relative to the workspace
should probably added.

Acknowledgements
================

* The Hudson scp plugin author for providing a great place to
start copy/pasting from
* http://github.com/stephenh/hudson-git2 - for this README.markdown
template and a great git plugin for hudson
* jets3t - http://jets3t.s3.amazonaws.com/index.html
