package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.ObjectMetadata;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.Destination;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public abstract class S3BaseUploadCallable extends S3Callable<String> {
    private static final long serialVersionUID = 1L;
    private final Destination dest;
    private final String storageClass;
    private final Map<String, String> userMetadata;
    private final boolean useServerSideEncryption;


    public S3BaseUploadCallable(String accessKey, Secret secretKey, boolean useRole, String endpoint,
                                Destination dest, Map<String, String> userMetadata, String storageClass, String selregion,
                                boolean useServerSideEncryption, ProxyConfiguration proxy) {
        super(accessKey, secretKey, useRole, endpoint, selregion, proxy);
        this.dest = dest;
        this.storageClass = storageClass;
        this.userMetadata = userMetadata;
        this.useServerSideEncryption = useServerSideEncryption;
    }

    /**
     * Upload from slave directly
     */
    @Override
    public String invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        return invoke(new FilePath(file));
    }

    /**
     * Stream from slave to master, then upload from master
     */
    public abstract String invoke(FilePath file) throws IOException, InterruptedException;

    protected ObjectMetadata buildMetadata(FilePath filePath) throws IOException, InterruptedException {
        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(Mimetypes.getInstance().getMimetype(filePath.getName()));
        metadata.setContentLength(filePath.length());
        metadata.setLastModified(new Date(filePath.lastModified()));
        if (storageClass != null && !storageClass.isEmpty()) {
            metadata.setHeader("x-amz-storage-class", storageClass);
        }
        if (useServerSideEncryption) {
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        }

        for (Map.Entry<String, String> entry : userMetadata.entrySet()) {
            final String key = entry.getKey().toLowerCase();
            switch (key) {
                case "cache-control":
                    metadata.setCacheControl(entry.getValue());
                    break;
                case "expires":
                    try {
                        final Date expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(entry.getValue());
                        metadata.setHttpExpiresDate(expires);
                    } catch (ParseException e) {
                        metadata.addUserMetadata(entry.getKey(), entry.getValue());
                    }
                    break;
                case "content-encoding":
                    metadata.setContentEncoding(entry.getValue());
                    break;
                case "content-type":
                    metadata.setContentType(entry.getValue());
                    break;
                default:
                    metadata.addUserMetadata(entry.getKey(), entry.getValue());
                    break;
            }
        }
        return metadata;
    }

    public Destination getDest() {
        return dest;
    }
}
