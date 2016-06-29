package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.model.ObjectMetadata;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.MD5;
import hudson.plugins.s3.Uploads;
import hudson.util.Secret;

import java.io.IOException;
import java.util.Map;

public final class S3UploadCallable extends S3BaseUploadCallable implements MasterSlaveCallable<String> {
    private static final long serialVersionUID = 1L;

    public S3UploadCallable(String accessKey, Secret secretKey, boolean useRole, Destination dest, Map<String, String> userMetadata, String storageClass, String selregion, boolean useServerSideEncryption, ProxyConfiguration proxy) {
        super(accessKey, secretKey, useRole, dest, userMetadata, storageClass, selregion, useServerSideEncryption, proxy);
    }

    /**
     * Stream from slave to master, then upload from master
     */
    @Override
    public String invoke(FilePath file) throws IOException, InterruptedException {
        final ObjectMetadata metadata = buildMetadata(file);

        Uploads.getInstance().startUploading(getTransferManager(), file, file.read(), getDest().bucketName, getDest().objectName, metadata);

        return MD5.generateFromFile(file);
    }
}