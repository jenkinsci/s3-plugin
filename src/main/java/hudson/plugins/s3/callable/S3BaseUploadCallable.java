package hudson.plugins.s3.callable;

import hudson.plugins.s3.Uploads;
import software.amazon.awssdk.core.internal.util.Mimetype;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.Destination;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

public abstract class S3BaseUploadCallable extends S3Callable<String> {
    private static final long serialVersionUID = 1L;
    private final Destination dest;
    private final String storageClass;
    private final Map<String, String> userMetadata;
    private final boolean useServerSideEncryption;


    public S3BaseUploadCallable(String accessKey, Secret secretKey, boolean useRole,
                                Destination dest, Map<String, String> userMetadata, String storageClass, String selregion,
                                boolean useServerSideEncryption, ProxyConfiguration proxy) {
        super(accessKey, secretKey, useRole, selregion, proxy);
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

    protected Uploads.Metadata buildMetadata(FilePath filePath) throws IOException, InterruptedException {
        long contentLength = filePath.length();
        Consumer<PutObjectRequest.Builder> builder = new Consumer<PutObjectRequest.Builder>() {
            @Override
            public void accept(PutObjectRequest.Builder metadata) {
                metadata.contentType(Mimetype.getInstance().getMimetype(new File(filePath.getName())));
                metadata.contentLength(contentLength);
                if (storageClass != null && !storageClass.isEmpty()) {
                    metadata.storageClass(storageClass);
                }
                if (useServerSideEncryption) {
                    metadata.sseCustomerAlgorithm("AES256");
                }
            }
        };
        Uploads.Metadata metadata = new Uploads.Metadata(builder);
        metadata.setContentLength(contentLength);
        for (Map.Entry<String, String> entry : userMetadata.entrySet()) {
            final String key = entry.getKey().toLowerCase();
            switch (key) {
                case "cache-control":
                    metadata.andThen(b1 -> b1.cacheControl(entry.getValue()));
                    break;
                case "expires":
                    try {
                        final Date expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(entry.getValue());
                        metadata.andThen(b1 -> b1.expires(expires.toInstant()));
                    } catch (ParseException e) {
                        metadata.putMetadata(entry.getKey(), entry.getValue());
                    }
                    break;
                case "content-encoding":
                    metadata.andThen(b1 -> b1.contentEncoding(entry.getValue()));
                    break;
                case "content-type":
                    metadata.andThen(b1 -> b1.contentType(entry.getValue()));
                    break;
                default:
                    metadata.putMetadata(entry.getKey(), entry.getValue());
                    break;
            }
        }
        return metadata;
    }

    public Destination getDest() {
        return dest;
    }
}
