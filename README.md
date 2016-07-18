
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

## Pipeline users

For Pipeline users, the same two actions are available via the
`step` step.

To upload a Tar GZip artifact named `${env.JOB_BASE_NAME}.tar.gz` to `my-bucket` bucket:

```groovy
node {
  stage "Upload artifact"
  step(
    [
      $class: 'S3BucketPublisher',
      entries: [
        [
          $class: 'Entry',
          bucket: "my-bucket/${env.JOB_BASE_NAME}",
          sourceFile: "${env.JOB_BASE_NAME}.tar.gz",
          selectedRegion: "us-east-1"
        ]
      ]
    ]
  )
}
```

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
