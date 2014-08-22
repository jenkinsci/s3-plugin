package hudson.plugins.s3.callable;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.plugins.s3.MetadataPair;
import hudson.plugins.s3.Redirect;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class S3UploadCallable extends AbstractS3Callable implements FileCallable<FingerprintRecord> {
    private static final long serialVersionUID = 1L;
    private final Destination dest;
    private final String storageClass;
    private final List<MetadataPair> userMetadata;
    private final Set<Redirect> redirects;
    private final String selregion;
    private final boolean produced;
    private final boolean useServerSideEncryption;

    public S3UploadCallable(boolean produced, String accessKey, Secret secretKey, boolean useRole, Destination dest, List<MetadataPair> userMetadata, Set<Redirect> redirects, String storageClass,
            String selregion, boolean useServerSideEncryption) {
        super(accessKey, secretKey, useRole);
        this.dest = dest;
        this.storageClass = storageClass;
        this.userMetadata = userMetadata;
        this.redirects = redirects;
        this.selregion = selregion;
        this.produced = produced;
        this.useServerSideEncryption = useServerSideEncryption;
    }

    public ObjectMetadata buildMetadata(FilePath filePath) throws IOException, InterruptedException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(Mimetypes.getInstance().getMimetype(filePath.getName()));
        metadata.setContentLength(filePath.length());
        metadata.setLastModified(new Date(filePath.lastModified()));
        if ((storageClass != null) && !"".equals(storageClass)) {
            metadata.setHeader("x-amz-storage-class", storageClass);
        }
        if (useServerSideEncryption) {
            metadata.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);	
        }
        	
        for (MetadataPair metadataPair : userMetadata) {
            metadata.addUserMetadata(metadataPair.key, metadataPair.value);
        }
        return metadata;
    }

    /**
     * Upload from slave directly
     */
    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        return invoke(new FilePath(file));
    }

    /**
     * Stream from slave to master, then upload from master
     */
    public FingerprintRecord invoke(FilePath file) throws IOException, InterruptedException {
        setRegion();
        PutObjectResult result = getClient().putObject(dest.bucketName, dest.objectName, file.read(), buildMetadata(file));

        for (Redirect redirect : redirects) {
            getClient().setObjectRedirectLocation(redirect.bucket, redirect.source, redirect.destination);
        }

        return new FingerprintRecord(produced, dest.bucketName, file.getName(), result.getETag());
    }

    private void setRegion() {
        Region region = RegionUtils.getRegion(Regions.valueOf(selregion).getName());
        getClient().setRegion(region);
    }
}
