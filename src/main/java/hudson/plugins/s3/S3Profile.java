package hudson.plugins.s3;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.plugins.s3.callable.S3UploadCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jenkins.model.Jenkins;

import org.apache.tools.ant.types.selectors.FileSelector;
import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;

public class S3Profile {
    private String name;
    private String accessKey;
    private String secretKey;
    private static final AtomicReference<AmazonS3Client> client = new AtomicReference<AmazonS3Client>(null);

    public S3Profile() {
    }

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey) {
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        client.set(new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey)));
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public final String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public final String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AmazonS3Client getClient() {
        if (client.get() == null) {
            client.set(new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey)));
        }
        return client.get();
    }

    public void check() throws Exception {
        getClient().listBuckets();
    }
    
    
   
    public FingerprintRecord upload(AbstractBuild<?,?> build, final BuildListener listener, String bucketName, FilePath filePath, boolean uploadFromSlave) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        
        Destination dest = Destination.newFromBuild(build, bucketName, filePath.getName());
        boolean produced = build.getTimeInMillis() <= filePath.lastModified()+2000;
        try {
          S3UploadCallable callable = new S3UploadCallable(produced, accessKey, secretKey, dest);
          if (uploadFromSlave) {
            return filePath.act(callable);
          } else {
            return callable.invoke(filePath);
          }
        } catch (Exception e) {
            listener.getLogger().println("error: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            throw new IOException("put " + dest + ": " + e);
        }
    }

//    public void download(Run build, String name, FilePath target) throws IOException, InterruptedException 
//    {
//      String buildName = build.getDisplayName();
//      int buildID = build.getNumber();
//      Destination dest = Destination.newFromRun(name,"jobs/" + buildName + "/" + buildID + "/" + name);
//      target.act(new S3DownloadCallable(accessKey, secretKey, dest));
//    }

    public List<String> list(Run build, String bucket, String expandedFilter) {
      AmazonS3Client s3client = getClient();        

      String buildName = build.getDisplayName();
      int buildID = build.getNumber();
      Destination dest = new Destination(bucket, "jobs/" + buildName + "/" + buildID + "/" + name);

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
     * @param build
     * @param artifacts
     * @param expandedFilter
     * @param targetDir
     * @param flatten
     * @return
     */
    public List<FingerprintRecord> downloadAll(Run build, List<FingerprintRecord> artifacts, String expandedFilter, FilePath targetDir, boolean flatten) {

        FilenameSelector selector = new FilenameSelector();
        selector.setName(expandedFilter);
        
        List<FingerprintRecord> fingerprints = Lists.newArrayList();
        for(FingerprintRecord record : artifacts) {
            S3Artifact artifact = record.artifact;
            if (selector.isSelected(new File("/"), artifact.getName(), null)) {
                Destination dest = Destination.newFromRun(build, artifact);
                FilePath target = new FilePath(targetDir, artifact.getName());
                try {
                    fingerprints.add(target.act(new S3DownloadCallable(accessKey, secretKey, dest)));
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

    public String getDownloadURL(Run build, FingerprintRecord record) {
        Destination dest = Destination.newFromRun(build, record.artifact);
        URL url = getClient().generatePresignedUrl(dest.bucketName, dest.objectName, new Date(System.currentTimeMillis() + 4000));
        return url.toExternalForm();
    }
}
