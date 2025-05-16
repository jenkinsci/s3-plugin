package hudson.plugins.s3.callable;

import hudson.ProxyConfiguration;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.MD5;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;

import java.io.File;
import java.io.IOException;

public final class S3DownloadCallable extends S3Callable<String>
{
    private static final long serialVersionUID = 1L;
    private final Destination dest;
    
    public S3DownloadCallable(String accessKey, Secret secretKey, boolean useRole, Destination dest, String region, ProxyConfiguration proxy)
    {
        super(accessKey, secretKey, useRole, region, proxy);
        this.dest = dest;
    }

    @Override
    public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException
    {
        final DownloadFileRequest req = DownloadFileRequest.builder()
                .getObjectRequest(builder -> builder.bucket(dest.bucketName).key(dest.objectName))
                .destination(file).build();
        FileDownload download = getTransferManager().downloadFile(req);

        download.completionFuture().join();

        return MD5.generateFromFile(file);
    }

}
