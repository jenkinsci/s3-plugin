[![Build Status](https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/s3-plugin)](https://jenkins.ci.cloudbees.com/job/plugins/job/s3-plugin/)

Install
=======

Tested with Jenkins 1.563

* Upload `target/s3.hpi` to your instance of Jenkins via
./pluginManager/advanced
* Configure S3 profile: Manage Jenkins -> Configure System ->
Amazon S3 profiles
* Project -> Configure -> [x] Publish artifacts to S3 Bucket

Building
========

Just run `mvn`.

Usage
=====

When activated, traditional (Freestyle) Jenkins builds will have a
build action called `S3 Copy Artifact` for downloading artifacts,
and a post-build action called `Publish Artifacts to S3 Bucket`.

For Pipeline users, the same two actions are available via the
`s3CopyArtifact` and `s3Upload` step. You can use the snippet generator to get started.

When using an Amazon S3 compatible storage system (OpenStack Swift, EMC Atmos...),
the list of AWS regions can be overridden specifying a file 
`classpath://com/amazonaws/partitions/override/endpoints.json` matching the format 
defined in AWS SDK's [endpoints.json](https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-core/src/main/resources/com/amazonaws/partitions/endpoints.json).

A solution to add this `endpoints.json` file in the classpath of Jenkins is to use the 
`java` command line parameter `-Xbootclasspath/a:/path/to/boot/classpath/folder/` and 
to locate `com/amazonaws/partitions/override/endpoints.json` in `/path/to/boot/classpath/folder/`.


Even if most of the features of the Jenkins S3 Plugin require the user to specify the target region,
some feature rely on a default Amazon S3 region which is by default the "US Standard Amazon S3 Region" 
and its endpoint `s3.amazonaws.com`. This default region can be overridden with the system property 
`hudson.plugins.s3.DEFAULT_AMAZON_S3_REGION`. 
Note that this default region name MUST match with a region define in the AWS SDK configuration file `endpoints.json`
(see above).

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
