package hudson.plugins.s3;

import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.plugins.s3.callable.S3UploadCallable;
import hudson.plugins.s3.cloudfront.InvalidationRecord;
import hudson.plugins.s3.cloudfront.callable.CloudFrontInvalidationCallable;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.ClientConfiguration;
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

public class S3Profile {
    private String name;
    private String accessKey;
    private Secret secretKey;
    private String proxyHost;
    private String proxyPort;
    private int maxUploadRetries;
    private int retryWaitTime;
    private transient volatile AmazonS3Client client = null;
    private ClientConfiguration clientConfiguration = null;
    private boolean useRole;
    private int signedUrlExpirySeconds = 60;

    public S3Profile() {
    }

    public S3Profile(String name, String accessKey, String secretKey, String proxyHost, String proxyPort, boolean useRole, String maxUploadRetries, String retryWaitTime) {
        /* The old hardcoded URL expiry was 4s, so: */
        this(name, accessKey, secretKey, proxyHost, proxyPort, useRole, 4, maxUploadRetries, retryWaitTime);
    }

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey, String proxyHost, String proxyPort, boolean useRole, int signedUrlExpirySeconds, String maxUploadRetries, String retryWaitTime) {
        this.name = name;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.useRole = useRole;
        try {
            this.maxUploadRetries = Integer.parseInt(maxUploadRetries);
        } catch(NumberFormatException nfe) {
            this.maxUploadRetries = 5;
        }
        try {
            this.retryWaitTime = Integer.parseInt(retryWaitTime);
        } catch(NumberFormatException nfe) {
            this.retryWaitTime = 5;
        }
        this.signedUrlExpirySeconds = signedUrlExpirySeconds;
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

    public final int getMaxUploadRetries() {
        return maxUploadRetries;
    }

    public final int getRetryWaitTime() {
        return retryWaitTime;
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

    public String getProxyHost() {
        return proxyHost;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public boolean isUseRole() {
        return useRole;
    }

    public int getSignedUrlExpirySeconds() {
        return signedUrlExpirySeconds;
    }

    public AmazonS3Client getClient() {
        if (client == null) {
            if (useRole) {
                client = new AmazonS3Client(getClientConfiguration());
            } else {
                client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey.getPlainText()), getClientConfiguration());
            }
        }
        return client;
    }

    private ClientConfiguration getClientConfiguration(){
        if (clientConfiguration == null) {
            clientConfiguration = new ClientConfiguration();

            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
                clientConfiguration.setProxyHost(proxy.name);
                clientConfiguration.setProxyPort(proxy.port);
                if(proxy.getUserName() != null) {
                    clientConfiguration.setProxyUsername(proxy.getUserName());
                    clientConfiguration.setProxyPassword(proxy.getPassword());
                }
            }
        }
        return clientConfiguration;
    }

    public void check() throws Exception {
        getClient().listBuckets();
    }

    public FingerprintRecord upload(AbstractBuild<?,?> build, final BuildListener listener, String bucketName, FilePath filePath, int searchPathLength, Map<String, String> userMetadata,
            String storageClass, String selregion, boolean uploadFromSlave, boolean managedArtifacts, boolean useServerSideEncryption, boolean flatten, boolean gzipFiles) throws IOException, InterruptedException {
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
            dest = Destination.newFromBuild(build, bucketName, filePath.getName());
            produced = build.getTimeInMillis() <= filePath.lastModified()+2000;
        }
        int retryCount = 0;

        while (true) {
            try {
                S3UploadCallable callable = new S3UploadCallable(produced, accessKey, secretKey, useRole, bucketName, dest, userMetadata, storageClass, selregion, useServerSideEncryption, gzipFiles);
                if (uploadFromSlave) {
                    return filePath.act(callable);
                } else {
                    return callable.invoke(filePath);
                }
            } catch (Exception e) {
                retryCount++;
                if(retryCount >= maxUploadRetries){
                    throw new IOException("put " + dest + ": " + e + ":: Failed after " + retryCount + " tries.", e);
                }
                Thread.sleep(retryWaitTime * 1000);
            }
        }
    }
	
    public InvalidationRecord invalidate(AbstractBuild<?, ?> build, BuildListener listener, String bucket, String keyPrefix) throws IOException, InterruptedException {
    	List<String> filesToInvalidate = list(new Destination(bucket, keyPrefix));
    	return invalidate(build, listener, bucket, filesToInvalidate);
	}
	
	

	private InvalidationRecord invalidate(AbstractBuild<?, ?> build, final BuildListener listener, String bucket, List<String> paths) throws IOException, InterruptedException {
		int retryCount = 0;
		while (true) {
			try {
				CloudFrontInvalidationCallable callable = new CloudFrontInvalidationCallable(accessKey, secretKey, useRole);
				return callable.invoke(bucket, paths);
			} catch (Exception e) {
				retryCount++;
				if (retryCount >= maxUploadRetries) {
					throw new IOException("invalidate paths "
							+ paths.toString() + ": " + e
							+ ":: Failed after " + retryCount + " tries.", e);
				}
				Thread.sleep(retryWaitTime * 1000);
			}
		}
	}

    public List<String> list(Run build, String bucket, String expandedFilter) {
        String buildName = build.getDisplayName();
        int buildID = build.getNumber();
        Destination dest = new Destination(bucket, "jobs/" + buildName + "/" + buildID + "/" + name);
        return list(dest);
      }

	private List<String> list(Destination dest) {
		AmazonS3Client s3client = getClient();        
		ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
        .withBucketName(dest.bucketName)
        .withPrefix(dest.objectName);

        List<String> files = Lists.newArrayList();
        
        ObjectListing objectListing;
        do {
          objectListing = s3client.listObjects(listObjectsRequest);
          for (S3ObjectSummary summary : objectListing.getObjectSummaries()) {
            GetObjectRequest req = new GetObjectRequest(dest.bucketName, summary.getKey());
            files.add(req.getKey());
          }
          listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());        
        return files;
      }

      /**
       * Download all artifacts from a given build
       */
      public List<FingerprintRecord> downloadAll(Run build, List<FingerprintRecord> artifacts, String expandedFilter, FilePath targetDir, boolean flatten, PrintStream console) {

          FilenameSelector selector = new FilenameSelector();
          selector.setName(expandedFilter);
          
          List<FingerprintRecord> fingerprints = Lists.newArrayList();
          for(FingerprintRecord record : artifacts) {
              S3Artifact artifact = record.artifact;
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
       * @param artifact
       */
      public void delete(Run build, FingerprintRecord record) {
          Destination dest = Destination.newFromRun(build, record.artifact);
          DeleteObjectRequest req = new DeleteObjectRequest(dest.bucketName, dest.objectName);
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
      public String getDownloadURL(Run build, FingerprintRecord record) {
          Destination dest = Destination.newFromRun(build, record.artifact);
          GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(dest.bucketName, dest.objectName);
          request.setExpiration(new Date(System.currentTimeMillis() + this.signedUrlExpirySeconds*1000));
          ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
          // let the browser use the last part of the name, not the full path
          // when saving.
          String fileName = (new File(dest.objectName)).getName().trim(); 
          headers.setContentDisposition("attachment; filename=\"" + fileName + "\"");
          request.setResponseHeaders(headers);
          URL url = getClient().generatePresignedUrl(request);
          return url.toExternalForm();
      }


    private Boolean shouldUseProxy(ProxyConfiguration proxy, String hostname) {
        if(proxy == null) {
            return false;
        }
        boolean shouldProxy = true;
        for(Pattern p : proxy.getNoProxyHostPatterns()) {
            if(p.matcher(hostname).matches()) {
                shouldProxy = false;
                break;
            }
        }

        return shouldProxy;
    }

}
