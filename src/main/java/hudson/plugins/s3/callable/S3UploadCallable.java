package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.plugins.s3.MetadataPair;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

public class S3UploadCallable extends AbstractS3Callable implements FileCallable<FingerprintRecord> {
    private static final long serialVersionUID = 1L;
    private final String bucketName;
    private final Destination dest;
    private final String fileName;
    private final String storageClass;
    private final List<MetadataPair> userMetadata;
    private final String selregion;
    private final boolean produced;
    private final boolean useServerSideEncryption;

    @Deprecated
    public S3UploadCallable(boolean produced, String accessKey, Secret secretKey, boolean useRole, Destination dest, String fileName, List<MetadataPair> userMetadata, String storageClass,
                            String selregion, boolean useServerSideEncryption) {
        this(produced, accessKey, secretKey, useRole, dest.bucketName, dest, fileName, userMetadata, storageClass, selregion, useServerSideEncryption);
    }

    public S3UploadCallable(boolean produced, String accessKey, Secret secretKey, boolean useRole, String bucketName, Destination dest, String fileName, List<MetadataPair> userMetadata, String storageClass,
            String selregion, boolean useServerSideEncryption) {
        super(accessKey, secretKey, useRole);
        this.bucketName = bucketName;
        this.dest = dest;
	this.fileName = fileName;
        this.storageClass = storageClass;
        this.userMetadata = userMetadata;
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
            String key = metadataPair.key.toLowerCase();
            if (key.equals("cache-control")) {
                metadata.setCacheControl(metadataPair.value);
            } else if (key.equals("expires")) {
                try {
                    Date expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(metadataPair.value);
                    metadata.setHttpExpiresDate(expires);
                } catch (ParseException e) {
                    metadata.addUserMetadata(metadataPair.key, metadataPair.value);
                }
            } else if (key.equals("content-encoding")) {
                metadata.setContentEncoding(metadataPair.value);
            } else {
                metadata.addUserMetadata(metadataPair.key, metadataPair.value);
            }
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

        File localFile = null;
        FileOutputStream os = null;
        boolean deleteLocalFile = false;

        try {
            if (file.isRemote()) {
                deleteLocalFile = true;
                localFile = File.createTempFile("s3plugin", ".bin");
                os = new FileOutputStream(localFile);
                file.copyTo(os);
                os.flush();
            } else {
                localFile = new File(file.getRemote());
            }

            final PutObjectRequest request = new PutObjectRequest(dest.bucketName, dest.objectName, localFile)
                .withMetadata(buildMetadata(file));
            final PutObjectResult result = getClient().putObject(request);
            return new FingerprintRecord(produced, bucketName, this.fileName, result.getETag());
        } finally {
            if (os != null) {
                os.close();
            }
            if (deleteLocalFile && localFile != null) {
                localFile.delete();
            }
        }
    }

    private void setRegion() {
        // In 0.7, selregion comes from Regions#name
        Region region = RegionUtils.getRegion(selregion);

        // In 0.6, selregion comes from Regions#valueOf
        if (region == null) {
            region = RegionUtils.getRegion(Regions.valueOf(selregion).getName());
        }

        getClient().setRegion(region);
    }
}
