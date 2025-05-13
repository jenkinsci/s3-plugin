package hudson.plugins.s3;

import com.google.common.collect.Lists;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.Run;
import hudson.plugins.s3.callable.MasterSlaveCallable;
import hudson.plugins.s3.callable.S3CleanupUploadCallable;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.plugins.s3.callable.S3GzipCallable;
import hudson.plugins.s3.callable.S3UploadCallable;
import hudson.plugins.s3.callable.S3WaitUploadCallable;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class S3Profile {
    private final String name;
    private final String accessKey;
    private final Secret secretKey;
    private final int maxUploadRetries;
    private final int uploadRetryTime;
    private final int maxDownloadRetries;
    private final int downloadRetryTime;
    private final boolean keepStructure;

    private final boolean useRole;
    private final int signedUrlExpirySeconds;

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey, boolean useRole, int signedUrlExpirySeconds, String maxUploadRetries, String uploadRetryTime, String maxDownloadRetries, String downloadRetryTime, boolean keepStructure) {
        this.name = name;
        this.useRole = useRole;
        this.maxUploadRetries = parseWithDefault(maxUploadRetries, 5);
        this.uploadRetryTime = parseWithDefault(uploadRetryTime, 5);
        this.maxDownloadRetries = parseWithDefault(maxDownloadRetries, 5);
        this.downloadRetryTime = parseWithDefault(downloadRetryTime, 5);
        this.signedUrlExpirySeconds = signedUrlExpirySeconds;
        if (useRole) {
            this.accessKey = "";
            this.secretKey = null;
        } else {
            this.accessKey = accessKey;
            this.secretKey = Secret.fromString(secretKey);
        }

        this.keepStructure = keepStructure;
    }

    public boolean isKeepStructure() {
        return keepStructure;
    }

    private int parseWithDefault(String number, int defaultValue) {
        try {
            return Integer.parseInt(number);
        } catch(NumberFormatException nfe) {
            return defaultValue;
        }
    }

    public int getMaxDownloadRetries() {
        return maxDownloadRetries;
    }

    public int getDownloadRetryTime() {
        return downloadRetryTime;
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public final Secret getSecretKey() {
        return secretKey;
    }

    public final int getMaxUploadRetries() {
        return maxUploadRetries;
    }

    public final int getUploadRetryTime() {
        return uploadRetryTime;
    }

    public /*final*/ String getName() {
        return this.name;
    }

    public final boolean getUseRole() {
        return this.useRole;
    }

    public boolean isUseRole() {
        return useRole;
    }

    public int getSignedUrlExpirySeconds() {
        return signedUrlExpirySeconds;
    }

    public S3Client getClient(String region) {
        return ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole, region, getProxy());
    }

    public List<FingerprintRecord> upload(Run<?, ?> run,
                                    final String bucketName,
                                    final List<FilePath> filePaths,
                                    final List<String> fileNames,
                                    final Map<String, String> userMetadata,
                                    final String storageClass,
                                    final String selregion,
                                    final boolean uploadFromSlave,
                                    final boolean managedArtifacts,
                                    final boolean useServerSideEncryption,
                                    final boolean gzipFiles) throws IOException, InterruptedException {
        final List<FingerprintRecord> fingerprints = new ArrayList<>(fileNames.size());

        try {
            for (int i = 0; i < fileNames.size(); i++) {
                final FilePath filePath = filePaths.get(i);
                final String fileName = fileNames.get(i);

                final Destination dest;
                final boolean produced;
                if (managedArtifacts) {
                    dest = Destination.newFromRun(run, bucketName, fileName, true);
                    produced = run.getTimeInMillis() <= filePath.lastModified() + 2000;
                } else {
                    dest = new Destination(bucketName, fileName);
                    produced = false;
                }

                final MasterSlaveCallable<String> upload;
                if (gzipFiles) {
                    upload = new S3GzipCallable(accessKey, secretKey, useRole, dest, userMetadata,
                            storageClass, selregion, useServerSideEncryption, getProxy());
                } else {
                    upload = new S3UploadCallable(accessKey, secretKey, useRole, dest, userMetadata,
                            storageClass, selregion, useServerSideEncryption, getProxy());
                }

                final FingerprintRecord fingerprintRecord = repeat(maxUploadRetries, uploadRetryTime, dest, new Callable<FingerprintRecord>() {
                    @Override
                    public FingerprintRecord call() throws IOException, InterruptedException {
                        final String md5 = invoke(uploadFromSlave, filePath, upload);
                        return new FingerprintRecord(produced, bucketName, fileName, selregion, md5);
                    }
                });

                fingerprints.add(fingerprintRecord);
            }

            waitUploads(filePaths, uploadFromSlave);
        } catch (InterruptedException | IOException exception) {
            cleanupUploads(filePaths, uploadFromSlave);
            throw exception;
        }

        return fingerprints;
    }

    private void cleanupUploads(final List<FilePath> filePaths, boolean uploadFromSlave) {
        for (FilePath filePath : filePaths) {
            try {
                invoke(uploadFromSlave, filePath, new S3CleanupUploadCallable());
            }
            catch (InterruptedException | IOException ignored) {
            }
        }
    }

    private void waitUploads(final List<FilePath> filePaths, boolean uploadFromSlave) throws InterruptedException, IOException {
        for (FilePath filePath : filePaths) {
            invoke(uploadFromSlave, filePath, new S3WaitUploadCallable());
        }
    }

    private <T> T invoke(boolean uploadFromSlave, FilePath filePath, MasterSlaveCallable<T> callable) throws InterruptedException, IOException {
        if (uploadFromSlave) {
            return filePath.act(callable);
        } else {
            return callable.invoke(filePath);
        }
    }

    public List<String> list(Run build, String bucket) {
        final var s3client = getClient(ClientHelper.DEFAULT_AMAZON_S3_REGION_NAME);

        final String buildName = build.getDisplayName();
        final int buildID = build.getNumber();
        final Destination dest = new Destination(bucket, "jobs/" + buildName + '/' + buildID + '/' + name);

        final ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .bucket(dest.bucketName)
                .prefix(dest.objectName)
                .encodingType("url").build();

        final List<String> files = Lists.newArrayList();
        for (S3Object summary : s3client.listObjects(listObjectsRequest).contents()) {
            final GetObjectRequest req = GetObjectRequest.builder().bucket(dest.bucketName).key(summary.key()).build();
            files.add(req.key());
        }


        return files;
    }

      /**
       * Download all artifacts from a given build
       */
      public List<FingerprintRecord> downloadAll(Run<?,?> build,
                                                 final List<FingerprintRecord> artifacts,
                                                 final String includeFilter,
                                                 final String excludeFilter,
                                                 final FilePath targetDir,
                                                 final boolean flatten) throws IOException, InterruptedException {
          final List<FingerprintRecord> fingerprints = Lists.newArrayList();
          for(final FingerprintRecord record : artifacts) {
              final S3Artifact artifact = record.getArtifact();
              final Destination dest = Destination.newFromRun(build, artifact);
              final FilePath target = getFilePath(targetDir, flatten, artifact.getName());

              if (FileHelper.selected(includeFilter, excludeFilter, artifact.getName())) {
                  fingerprints.add(repeat(maxDownloadRetries, downloadRetryTime, dest, new Callable<FingerprintRecord>() {
                      @Override
                      public FingerprintRecord call() throws IOException, InterruptedException {
                          final String md5 = target.act(new S3DownloadCallable(accessKey, secretKey, useRole, dest, artifact.getRegion(), getProxy()));
                          return new FingerprintRecord(true, dest.bucketName, target.getName(), artifact.getRegion(), md5);
                      }
                  }));
              }
          }
          return fingerprints;
      }

    private <T> T repeat(int maxRetries, int waitTime, Destination dest, Callable<T> func) throws IOException, InterruptedException {
        int retryCount = 0;

        while (true) {
            try {
                return func.call();
            } catch (Exception e) {
                retryCount++;
                if(retryCount >= maxRetries){
                    throw new IOException("Call fails for " + dest + ": " + e + ":: Failed after " + retryCount + " tries.", e);
                }
                Thread.sleep(TimeUnit.SECONDS.toMillis(waitTime));
            }
        }
    }

    private FilePath getFilePath(FilePath targetDir, boolean flatten, String fullName) {
        if (flatten) {
            return new FilePath(targetDir, FilenameUtils.getName(fullName));
        }
        else  {
            return new FilePath(targetDir, fullName);
        }
    }

    /**
       * Delete some artifacts of a given run
       */
      public void delete(Run run, FingerprintRecord record) {
          final Destination dest = Destination.newFromRun(run, record.getArtifact());
          final DeleteObjectRequest req = DeleteObjectRequest.builder().bucket(dest.bucketName).key(dest.objectName).build();
          final var client = getClient(record.getArtifact().getRegion());
          client.deleteObject(req);
      }

    @Override
    public String toString() {
        return "S3Profile{" +
                "name='" + name + '\'' +
                ", accessKey='" + accessKey + '\'' +
                ", secretKey=" + secretKey +
                ", useRole=" + useRole +
                '}';
    }

    private ProxyConfiguration getProxy() {
        return Jenkins.get().proxy;
    }
}
