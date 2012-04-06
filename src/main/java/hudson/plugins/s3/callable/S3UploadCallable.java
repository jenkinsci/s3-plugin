package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;

public class S3UploadCallable extends AbstractS3Callable implements FileCallable<FingerprintRecord> 
{
    
    private static final long serialVersionUID = 1L;
    final private Destination dest;
    final private boolean produced;

    public S3UploadCallable(boolean produced, String accessKey, String secretKey, Destination dest) 
    {
        super(accessKey, secretKey);
        this.dest = dest;
        this.produced = produced;
    }

    /**
     * Remote on slave variant
     */
    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException 
    {
        PutObjectResult result = getClient().putObject(dest.bucketName, dest.objectName, file);
        return new FingerprintRecord(produced, dest.bucketName, file.getName(), result.getETag());
    }

    /**
     * Stream from slave, upload on master variant
     */
    public FingerprintRecord invoke(FilePath file) throws IOException, InterruptedException 
    {
        ObjectMetadata md = new ObjectMetadata();
        md.setContentLength(file.length());
        md.setLastModified(new Date(file.lastModified()));
        PutObjectResult result = getClient().putObject(dest.bucketName, dest.objectName, file.read(), md);
        return new FingerprintRecord(produced, dest.bucketName, file.getName(), result.getETag());
    }
}