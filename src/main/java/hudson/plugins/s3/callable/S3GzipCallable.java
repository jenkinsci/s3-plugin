package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.model.ObjectMetadata;
import hudson.FilePath;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.MD5;
import hudson.plugins.s3.S3Profile;
import hudson.plugins.s3.Uploads;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class S3GzipCallable extends S3BaseUploadCallable implements MasterSlaveCallable<String> {
    public S3GzipCallable(S3Profile profile, String selregion,
                          Destination dest, Map<String, String> userMetadata, String storageClass,
                          boolean useServerSideEncryption) {
        super(profile, selregion, dest, userMetadata, storageClass, useServerSideEncryption);
    }

    @Override
    public String invoke(FilePath file) throws IOException, InterruptedException {
        final File localFile = File.createTempFile("s3plugin", ".bin");

        try (InputStream inputStream = file.read()) {
            try (OutputStream outputStream = new FileOutputStream(localFile)) {
                try (OutputStream gzipStream = new GZIPOutputStream(outputStream, true)) {
                    IOUtils.copy(inputStream, gzipStream);
                    gzipStream.flush();
                }
            }
        }

        final InputStream gzipedStream = new FileInputStream(localFile);
        final ObjectMetadata metadata = buildMetadata(file);
        metadata.setContentEncoding("gzip");
        metadata.setContentLength(localFile.length());

        Uploads.getInstance().startUploading(getTransferManager(), file, gzipedStream, getDest().bucketName, getDest().objectName, metadata);

        return MD5.generateFromFile(localFile);
    }
}
