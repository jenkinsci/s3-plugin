package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class S3DownloadCallable extends AbstractS3Callable implements FileCallable<FingerprintRecord> 
{
    private static final long serialVersionUID = 1L;
    final private Destination dest;
    
    public S3DownloadCallable(String accessKey, Secret secretKey, boolean useRole, Destination dest)
    {
        super(accessKey, secretKey, useRole);
        this.dest = dest;
    }

    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException 
    {
        GetObjectRequest req = new GetObjectRequest(dest.bucketName, dest.objectName);
        Download download = getTransferManager().download(req, file);

        download.waitForCompletion();

        InputStream inputStream = new FileInputStream(file.getAbsolutePath());

        String md5 = getMD5(inputStream);

        return new FingerprintRecord(true, dest.bucketName, file.getName(), md5);
    }

}
