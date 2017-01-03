package hudson.plugins.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.plugins.s3.callable.MasterSlaveCallable;
import hudson.plugins.s3.callable.S3CleanupUploadCallable;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.plugins.s3.callable.S3GzipCallable;
import hudson.plugins.s3.callable.S3UploadCallable;
import hudson.plugins.s3.callable.S3WaitUploadCallable;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class S3Profile implements Describable<S3Profile> {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final String name;
    private final String endpointUrl;
    private final boolean pathStyleAccess;
    private final boolean payloadSigningEnabled;
    private final String accessKey;
    private final Secret secretKey;
    private final String defaultBucket;
    private final int maxUploadRetries;
    private final int uploadRetryTime;
    private final int maxDownloadRetries;
    private final int downloadRetryTime;
    private transient volatile AmazonS3Client client;
    private final boolean keepStructure;

    private final boolean useRole;
    private final int signedUrlExpirySeconds;

    @DataBoundConstructor
    public S3Profile(String name, String endpointUrl, String accessKey, String secretKey, String defaultBucket, boolean useRole, int signedUrlExpirySeconds, String maxUploadRetries, String uploadRetryTime, String maxDownloadRetries, String downloadRetryTime, boolean keepStructure, boolean pathStyleAccess, boolean payloadSigningEnabled) {
        this.name = name;
        this.useRole = useRole;
        this.maxUploadRetries = parseWithDefault(maxUploadRetries, 5);
        this.uploadRetryTime = parseWithDefault(uploadRetryTime, 5);
        this.maxDownloadRetries = parseWithDefault(maxDownloadRetries, 5);
        this.downloadRetryTime = parseWithDefault(downloadRetryTime, 5);
        this.signedUrlExpirySeconds = signedUrlExpirySeconds;
        this.endpointUrl = endpointUrl;
        this.pathStyleAccess = pathStyleAccess;
        this.payloadSigningEnabled = payloadSigningEnabled;
        if (useRole) {
            this.accessKey = "";
            this.secretKey = null;
        } else {
            this.accessKey = accessKey;
            this.secretKey = Secret.fromString(secretKey);
        }
        this.defaultBucket = defaultBucket;

        this.keepStructure = keepStructure;
    }

    @Override
    public Descriptor<S3Profile> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
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

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public boolean isPayloadSigningEnabled() {
        return payloadSigningEnabled;
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public final Secret getSecretKey() {
        return secretKey;
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public final int getMaxUploadRetries() {
        return maxUploadRetries;
    }

    public final int getUploadRetryTime() {
        return uploadRetryTime;
    }

    public final String getName() {
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


    public AmazonS3Client getClient() {
        if (client == null) {
            client = ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole, ClientHelper.DEFAULT_AMAZON_S3_REGION_NAME, getProxy(), getEndpointUrl(), isPathStyleAccess(), isPayloadSigningEnabled());
        }
        return client;
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
                final String uploadBucket = S3Artifact.getBucket(bucketName, getDefaultBucket());
                if (managedArtifacts) {
                    dest = Destination.newFromRun(run, uploadBucket, fileName, true);
                    produced = run.getTimeInMillis() <= filePath.lastModified() + 2000;
                } else {
                    dest = new Destination(uploadBucket, fileName);
                    produced = false;
                }

                final MasterSlaveCallable<String> upload;
                if (gzipFiles) {
                    upload = new S3GzipCallable(this, selregion, dest, userMetadata, storageClass, useServerSideEncryption);
                } else {
                    upload = new S3UploadCallable(this, selregion, dest, userMetadata, storageClass, useServerSideEncryption);
                }

                final FingerprintRecord fingerprintRecord = repeat(maxUploadRetries, uploadRetryTime, dest, new Callable<FingerprintRecord>() {
                    @Override
                    public FingerprintRecord call() throws IOException, InterruptedException {
                        final String md5 = invoke(uploadFromSlave, filePath, upload);
                        return new FingerprintRecord(produced, uploadBucket, fileName, selregion, md5);
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
        final AmazonS3Client s3client = getClient();

        final String buildName = build.getDisplayName();
        final int buildID = build.getNumber();
        final String uploadBucket = S3Artifact.getBucket(bucket, getDefaultBucket());
        final Destination dest = new Destination(uploadBucket, "jobs/" + buildName + '/' + buildID + '/' + name);

        final ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
        .withBucketName(dest.bucketName)
        .withPrefix(dest.objectName);

        final List<String> files = Lists.newArrayList();

        ObjectListing objectListing;
        do {
          objectListing = s3client.listObjects(listObjectsRequest);
          for (S3ObjectSummary summary : objectListing.getObjectSummaries()) {
            final GetObjectRequest req = new GetObjectRequest(dest.bucketName, summary.getKey());
            files.add(req.getKey());
          }
          listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
        return files;
      }

      /**
       * Download all artifacts from a given build
       */
      public List<FingerprintRecord> downloadAll(Run build,
                                                 final List<FingerprintRecord> artifacts,
                                                 final String includeFilter,
                                                 final String excludeFilter,
                                                 final FilePath targetDir,
                                                 final boolean flatten) throws IOException, InterruptedException {
          final List<FingerprintRecord> fingerprints = Lists.newArrayList();
          for(final FingerprintRecord record : artifacts) {
              final S3Artifact artifact = record.getArtifact();
              final Destination dest = Destination.newFromRun(build, artifact, getDefaultBucket());
              final FilePath target = getFilePath(targetDir, flatten, artifact.getName());

              if (FileHelper.selected(includeFilter, excludeFilter, artifact.getName())) {
                  fingerprints.add(repeat(maxDownloadRetries, downloadRetryTime, dest, new Callable<FingerprintRecord>() {
                      @Override
                      public FingerprintRecord call() throws IOException, InterruptedException {
                          final String md5 = target.act(new S3DownloadCallable(S3Profile.this, dest, artifact.getRegion()));
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
          final Destination dest = Destination.newFromRun(run, record.getArtifact(), getDefaultBucket());
          final DeleteObjectRequest req = new DeleteObjectRequest(dest.bucketName, dest.objectName);
          getClient().deleteObject(req);
      }


      /**
       * Generate a signed download request for a redirect from s3/download.
       *
       * When the user asks to download a file, we sign a short-lived S3 URL
       * for them and redirect them to it, so we don't have to proxy for the
       * download and there's no need for the user to have credentials to
       * access S3.
       */
      public String getDownloadURL(Run run, FingerprintRecord record) {
          final Destination dest = Destination.newFromRun(run, record.getArtifact(), getDefaultBucket());
          final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(dest.bucketName, dest.objectName);
          request.setExpiration(new Date(System.currentTimeMillis() + this.signedUrlExpirySeconds*1000));
          final ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
          // let the browser use the last part of the name, not the full path
          // when saving.
          final String fileName = (new File(dest.objectName)).getName().trim();
          headers.setContentDisposition("attachment; filename=\"" + fileName + '"');
          request.setResponseHeaders(headers);
          return getClient().generatePresignedUrl(request).toExternalForm();
      }

    public S3Objects getObjectsWithPrefix(String bucket) {
        final String bucketName = S3Artifact.getJustBucketName(bucket, getDefaultBucket());
        final String prefix = S3Artifact.getPrefix(bucket, getDefaultBucket());
        return S3Objects.withPrefix(getClient(), bucketName, prefix);
    }

    @Override
    public String toString() {
        return "S3Profile{" +
                "name='" + name + '\'' +
                ", accessKey='" + accessKey + '\'' +
                ", secretKey=" + secretKey +
                ", useRole=" + useRole +
                ", endpointUrl=" + endpointUrl +
                ", pathStyleAccess=" + pathStyleAccess +
                ", payloadSigningEnabled=" + payloadSigningEnabled +
                ", defaultBucket=" + defaultBucket +
                '}';
    }

    public ProxyConfiguration getProxy() {
        return Jenkins.getActiveInstance().proxy;
    }

    public static final class DescriptorImpl extends Descriptor<S3Profile> {

        private final CopyOnWriteList<S3Profile> profiles = new CopyOnWriteList<S3Profile>();
        public static final Level[] consoleLogLevels = { Level.INFO, Level.WARNING, Level.SEVERE };
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());
        private static final Result[] pluginFailureResultConstraints = { Result.FAILURE, Result.UNSTABLE, Result.SUCCESS };

        public DescriptorImpl(Class<? extends S3Profile> clazz) {
            super(clazz);
            load();
        }

        public List<Region> regions = UploadEntry.regions;

        public String[] storageClasses = UploadEntry.storageClasses;

        public DescriptorImpl() {
            this(S3Profile.class);
        }

        @Override
        public String getDisplayName() {
            return "Publish artifacts to S3 Bucket";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/s3/help.html";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            final JSONArray array = json.optJSONArray("profile");
            if (array != null) {
                profiles.replaceBy(req.bindJSONToList(S3Profile.class, array));
            } else {
                profiles.replaceBy(req.bindJSON(S3Profile.class, json.getJSONObject("profile")));
            }
            save();
            return true;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillProfileNameItems() {
            final ListBoxModel model = new ListBoxModel();
            for (S3Profile profile : profiles) {
                model.add(profile.getName(), profile.getName());
            }
            return model;
        }

        public ListBoxModel doFillConsoleLogLevelItems() {
            final ListBoxModel model = new ListBoxModel();
            for (Level l : consoleLogLevels) {
                model.add(l.getName(), l.getLocalizedName());
            }
            return model;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillPluginFailureResultConstraintItems() {
            final ListBoxModel model = new ListBoxModel();
            for (Result r : pluginFailureResultConstraints) {
                model.add(r.toString(), r.toString());
            }
            return model;
        }

        @SuppressWarnings("unused")
        public void replaceProfiles(List<S3Profile> profiles) {
            this.profiles.replaceBy(profiles);
            save();
        }

        public Level[] getConsoleLogLevels() {
            return consoleLogLevels;
        }

        public S3Profile[] getProfiles() {
            final S3Profile[] profileArray = new S3Profile[profiles.size()];
            return profiles.toArray(profileArray);
        }

        public Result[] getPluginFailureResultConstraints() {
            return pluginFailureResultConstraints;
        }

        @SuppressWarnings("unused")
        public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) {
            final String name = Util.fixNull(req.getParameter("name"));
            final String accessKey = Util.fixNull(req.getParameter("accessKey"));
            final String secretKey = Util.fixNull(req.getParameter("secretKey"));
            final String useIAMCredential = Util.fixNull(req.getParameter("useRole"));
            final String endpointUrl = req.getParameter("endpointUrl");
            final boolean pathStyleAccess = Boolean.parseBoolean(Util.fixNull(req.getParameter("pathStyleAccess")));
            final boolean payloadSigningEnabled = Boolean.parseBoolean(Util.fixNull(req.getParameter("payloadSigningEnabled")));


            final boolean couldBeValidated = !name.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty();
            final boolean useRole = Boolean.parseBoolean(useIAMCredential);

            if (!couldBeValidated) {
                if (name.isEmpty())
                    return FormValidation.ok("Please, enter name");

                if (useRole)
                    return FormValidation.ok();

                if (accessKey.isEmpty())
                    return FormValidation.ok("Please, enter accessKey");

                if (secretKey.isEmpty())
                    return FormValidation.ok("Please, enter secretKey");
            }

            final AmazonS3Client client = ClientHelper.createClient(accessKey, secretKey, useRole, ClientHelper.DEFAULT_AMAZON_S3_REGION_NAME, Jenkins.getActiveInstance().proxy, endpointUrl, pathStyleAccess, payloadSigningEnabled);

            try {
                client.listBuckets();
            } catch (AmazonClientException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                return FormValidation.error("Can't connect to S3 service: " + e.getMessage());
            }
            return FormValidation.ok("Check passed!");
        }

    }

}
