package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.MD5;
import hudson.plugins.s3.S3Profile;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;

public final class S3DownloadCallable extends S3Callable<String>
{
    private static final long serialVersionUID = 1L;
    private final Destination dest;

    public S3DownloadCallable(S3Profile profile, Destination dest, String region)
    {
        super(profile, region);
        this.dest = dest;
    }

    @Override
    public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException
    {
        final GetObjectRequest req = new GetObjectRequest(dest.bucketName, dest.objectName);
        final Download download = getTransferManager().download(req, file);

        download.waitForCompletion();

        return MD5.generateFromFile(file);
    }

}
