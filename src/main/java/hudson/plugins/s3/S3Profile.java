package hudson.plugins.s3;

import com.amazonaws.ClientConfiguration;
import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Date;
import java.util.List;

import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;

import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.plugins.s3.callable.S3UploadCallable;
import hudson.util.Secret;

public class S3Profile {
    private String name;
    private String accessKey;
    private Secret secretKey;
    private String proxyHost;
    private String proxyPort;
    private transient volatile AmazonS3Client client = null;
    private ClientConfiguration clientConfiguration = null;
    private boolean useRole;

    public S3Profile() {
    }

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey, String proxyHost, String proxyPort, boolean useRole) {
        this.name = name;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.useRole = useRole;
        if (useRole) {
            this.accessKey = "";
            this.secretKey = null;
        } else {
            this.accessKey = accessKey;
            this.secretKey = Secret.fromString(secretKey);
        }
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public final Secret getSecretKey() {
        return secretKey;
    }

    public final String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public final boolean getUseRole() {
        return this.useRole;
    }

    public void setUseRole(boolean useRole) {
        this.useRole = useRole;
    }

    public AmazonS3Client getClient() {
        if (client == null) {
            if (useRole) {
                client = new AmazonS3Client(getClientConfiguration());
            } else {
                client = new AmazonS3Client(
                        new BasicAWSCredentials(accessKey, secretKey.getPlainText()), getClientConfiguration()
                );
            }
        }
        return client;
    }

    private ClientConfiguration getClientConfiguration(){
        if (clientConfiguration == null) {
            clientConfiguration = new ClientConfiguration();
            if(proxyHost != null && proxyHost.length() > 0) {
                clientConfiguration.setProxyHost(proxyHost);
                clientConfiguration.setProxyPort(Integer.parseInt(proxyPort));
            }
        }
        return clientConfiguration;
    }

    public void check() throws Exception {
        getClient().listBuckets();
    }

    public FingerprintRecord upload(AbstractBuild<?,?> build, final BuildListener listener, String bucketName,
                                    FilePath filePath, int searchPathLength, List<MetadataPair> userMetadata,
                                    String storageClass, String selregion, boolean uploadFromSlave,
                                    boolean managedArtifacts, boolean useServerSideEncryption, boolean flatten)
            throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }

        String fileName = null;
        if (flatten) {
            fileName = filePath.getName();
        } else {
            String relativeFileName = filePath.getRemote();
            fileName = relativeFileName.substring(searchPathLength);
        }

        Destination dest = new Destination(bucketName, fileName);
        boolean produced = false;
        if (managedArtifacts) {
            //TODO dest = Destination.newFromBuild(build, bucketName, fileName);
            dest = Destination.newFromBuild(build, bucketName, filePath.getName());
            produced = build.getTimeInMillis() <= filePath.lastModified()+2000;
        }

        try {
            S3UploadCallable callable = new S3UploadCallable(
                    produced, accessKey, secretKey, useRole, dest, userMetadata,
                    storageClass, selregion,useServerSideEncryption
            );
            if (uploadFromSlave) {
                return filePath.act(callable);
            } else {
                return callable.invoke(filePath);
            }
        } catch (Exception e) {
            throw new IOException("put " + dest + ": " + e);
        }
    }

    public List<String> list(Run build, String bucket, String expandedFilter) {
        AmazonS3Client s3client = getClient();
        Destination dest = Destination.newFromRun(build, bucket, name);

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
        .withBucketName(dest.getBucketName())
        .withPrefix(dest.getObjectName());

        List<String> files = Lists.newArrayList();
        
        ObjectListing objectListing;
        do {
          objectListing = s3client.listObjects(listObjectsRequest);
          for (S3ObjectSummary summary : objectListing.getObjectSummaries()) {
            GetObjectRequest req = new GetObjectRequest(dest.getBucketName(), summary.getKey());
            files.add(req.getKey());
          }
          listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());        
        return files;
      }

      /**
       * Download all artifacts from a given build
       */
      public List<FingerprintRecord> downloadAll(Run build, List<FingerprintRecord> artifacts, String expandedFilter,
                                                 FilePath targetDir, boolean flatten, PrintStream console) {

          FilenameSelector selector = new FilenameSelector();
          selector.setName(expandedFilter);
          
          List<FingerprintRecord> fingerprints = Lists.newArrayList();
          for(FingerprintRecord record : artifacts) {
              S3Artifact artifact = record.getArtifact();
              if (selector.isSelected(new File("/"), artifact.getName(), null)) {
                  Destination dest = Destination.newFromRun(build, artifact);
                  FilePath target = new FilePath(targetDir, artifact.getName());
                  try {
                      fingerprints.add(target.act(new S3DownloadCallable(accessKey, secretKey, useRole, dest, console)));
                  } catch (IOException e) {
                      e.printStackTrace();
                  } catch (InterruptedException e) {
                      e.printStackTrace();
                  }
              }
          }
          return fingerprints;
      }

      /**
       * Delete some artifacts of a given run
       * @param build
       * @param record
       */
      public void delete(Run build, FingerprintRecord record) {
          Destination dest = Destination.newFromRun(build, record.getArtifact());
          DeleteObjectRequest req = new DeleteObjectRequest(dest.getBucketName(), dest.getObjectName());
          getClient().deleteObject(req);
      }

      public String getDownloadURL(Run build, FingerprintRecord record) {
          Destination dest = Destination.newFromRun(build, record.getArtifact());
          GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(dest.getBucketName(), dest.getObjectName());
          request.setExpiration(new Date(System.currentTimeMillis() + 4000));
          ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
          // let the browser use the last part of the name, not the full path
          // when saving.
          String fileName = (new File(dest.getObjectName())).getName().trim();
          headers.setContentDisposition("attachment; filename=\"" + fileName + "\"");
          request.setResponseHeaders(headers);
          URL url = getClient().generatePresignedUrl(request);
          return url.toExternalForm();
      }

}
