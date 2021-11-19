# Version 0.10.11 (Dec 31, 2016) 
**do not update - backward compatibility for pipeline scripts are broken**
* Make plugin compatible with storage backends compatible with Amazon S3 (OpenStack Swift...) (JENKINS-40654, PR-100)
* Add Standard - Infrequent Access storage class (PR-98)
* Constrain build result severity (JENKINS-27284, PR-95)
* Add job setting to suppress console logging (PR-94)

# Version 0.10.10 (Oct 10, 2016)
* Add method for changing S3Profile via GroovyVersion

# Version 0.10.9 (June 27, 2016)
* Added option to open content directly in browser (JENKINS-37346)
* FIXED IE and Chrome download issue when file path is window style ([PR-93|https://github.com/jenkinsci/s3-plugin/pull/93)

# Version 0.10.8 (Aug 31, 2016)
**Doesn't exist (broken release because of changes in Jenkins plugin repository)**

#Version 0.10.7 (July 21, 2016)
* Handle InterruptedExceptions that no files are found (PR-92)

# Version 0.10.6 (July 1, 2016)
* Don't upload on aborted build (JENKINS-25509, PR-90)

# Version 0.10.5.1 (June 27, 2016)
* Plugin missing transitive dependencies ( JENKINS-36096 )

# Version 0.10.5 (June 17, 2016)
* Failed to reset the request input stream (JENKINS-34216 / PR-90 )

# Version 0.10.4 (June 10, 2016)
* Restore support for MatrixPlugin (JENKINS-35123)
* Add new parameter on Profile level - to keep or not to folder structure. By default, plugin doesn't keep folder structure. And option to keep structure will be removed in some of next releases (JENKINS-34780)

# Version 0.10.3 (May 25, 2016)
* Add option to keep artifacts forever
* S3 Plugin switches credential profiles on-the-fly (JENKINS-14470)

# Version 0.10.2 (May 11, 2016)
* Add usages to README file (PR-87)
* Add option to set content-type on files (PR-86)
* S3 artifacts are visible from API

# Version 0.10.1 (Apr 25, 2016)
* Parallel uploading
* Support uploading for unfinished builds

# Version 0.9.4 (Apr 23, 2016)
* Update AWS SDK to latest version
* Fix credential issue

# Version 0.9.2 (Apr 06, 2016)
* Update AWS SDK to latest version
* Fix credential issue

# Version 0.9.1 (Apr 05, 2016)
* Updated the aws-java-sdk dependency to support java region uploads
* Uploading and downloading files more than 5GB using TransferManager
* Flatten directories
* Excludes for downloading and uploading
* Several profiles
* Retries for downloading
* Workflow plugin support
* Using default Jenkins proxy
* Now artifacts are using full name instead of project name only

# Version 0.5 (Aug 09, 2013)
* Added Regions Support (JENKINS-18839)
* Update AWS SDK to latest version

# Version 0.4 (Jul 12, 2013)
* Added storage class support
* Added arbitrary metadata support
* Fixed the problem where the plugin messes up credential profiles upon concurrent use (JENKINS-14470)
* Plugin shouldn't store S3 password in clear (JENKINS-14395)

# Version 0.3.1 (Sept. 20th, 2012)
* Prevent OOME when uploading large files.
* Update Amazon SDK

# Version 0.3.0 (May 29th, 2012)
* Use AWS MimeType library to determine the Content-Type of the uploaded file.