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
    
    public S3DownloadCallable(String accessKey, Secret secretKey, String proxyHost, String proxyPort, boolean useRole, Destination dest, PrintStream console) 
    {
        super(accessKey, secretKey, proxyHost, proxyPort, useRole);
        this.dest = dest;
        this.log = console;
    }

    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException 
    {
        GetObjectRequest req = new GetObjectRequest(dest.bucketName, dest.objectName);
        ObjectMetadata md = getClient().getObject(req, file);

        return new FingerprintRecord(true, dest.bucketName, file.getName(), md.getETag());
    }

}
