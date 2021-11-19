
This plugin helps one to upload build artifacts to Amazon S3.

## Making artifacts public

If you'd like to have some of your artifacts be publicly downloadable,
see [Granting public access to some S3 objects](https://aws.amazon.com/premiumsupport/knowledge-center/read-access-objects-s3-bucket/)

Usage
=====

When activated, traditional (Freestyle) Jenkins builds will have a
build action called `S3 Copy Artifact` for downloading artifacts,
and a post-build action called `Publish Artifacts to S3 Bucket`.

For Pipeline users, the same two actions are available via the
`s3CopyArtifact` and `s3Upload` step. You can use the snippet generator to get started.

When using an Amazon S3 compatible storage system (OpenStack Swift, EMC Atmos...),
the list of AWS regions can be overridden by specifying a file 
`classpath://com/amazonaws/partitions/override/endpoints.json` matching the format 
defined in AWS SDK's [endpoints.json](https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-core/src/main/resources/com/amazonaws/partitions/endpoints.json).

A solution to add this `endpoints.json` file in the classpath of Jenkins is to use the 
`java` command line parameter `-Xbootclasspath/a:/path/to/boot/classpath/folder/` and 
to locate `com/amazonaws/partitions/override/endpoints.json` in `/path/to/boot/classpath/folder/`.


Even if most of the features of the Jenkins S3 Plugin require the user to specify the target region,
some feature rely on a default Amazon S3 region which is by default the "US Standard Amazon S3 Region" 
and its endpoint is `s3.amazonaws.com`. This default region can be overridden with the system property 
`hudson.plugins.s3.DEFAULT_AMAZON_S3_REGION`. 
Note that this default region name MUST match with a region define in the AWS SDK configuration file `endpoints.json`
(see above).

Usage with IAM
=====

If you used IAM to create a separate pair of access credentials for this
plugin, you can lock down its AWS access to simply listing buckets and
writing to a specific bucket. Add the following custom policy to the
user in the IAM console, replacing occurrences of "my-artifact-bucket"
with your bucket name, which you'll have to create first:

``` json
{
  "Statement": [
    {
      "Action": [
        "s3:ListAllMyBuckets"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Action": "s3:*",
      "Effect": "Allow",
      "Resource": ["arn:aws:s3:::my-artifact-bucket", "arn:aws:s3:::my-artifact-bucket/*"]
    }
  ]
}
```

Notes
=====

* Only the basename of source files is used as the object key name,
an option to include the path name relative to the workspace
should probably be added.

Changelog
=========

* New change logs are in [GitHub Releases](https://github.com/jenkinsci/s3-plugin/releases)
* Old change logs are stored in [old-changelog.md](old-changelog.md).

Acknowledgements
================

* The Hudson scp plugin author for providing a great place to
start copy/pasting from.
* http://github.com/stephenh/hudson-git2 - for this README.markdown
template and a great git plugin for hudson.
* jets3t - http://jets3t.s3.amazonaws.com/index.html
