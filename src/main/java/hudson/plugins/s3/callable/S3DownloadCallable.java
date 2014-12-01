package hudson.plugins.s3.callable;

import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class S3DownloadCallable extends AbstractS3Callable implements FileCallable<FingerprintRecord> 
{
    private static final long serialVersionUID = 1L;
    final private Destination dest;
    final transient private PrintStream log;
    
    public S3DownloadCallable(String accessKey, Secret secretKey, boolean useRole, Destination dest, PrintStream console) 
    {
        super(accessKey, secretKey, useRole);
        this.dest = dest;
        this.log = console;
    }

    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException 
    {
        GetObjectRequest req = new GetObjectRequest(dest.getBucketName(), dest.getObjectName());
        ObjectMetadata md = getClient().getObject(req, file);

        return new FingerprintRecord(true, dest.getUserBucketName(), file.getName(), md.getETag());
        //TODO return new FingerprintRecord(true, dest.getUserBucketName(), dest.getFileName(), md.getETag());
    }

}
