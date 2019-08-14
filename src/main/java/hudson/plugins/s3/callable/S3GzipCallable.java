package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.event.ProgressEvent;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.MD5;
import hudson.plugins.s3.Uploads;
import hudson.util.Secret;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class S3GzipCallable extends S3BaseUploadCallable implements MasterSlaveCallable<String> {
    public S3GzipCallable(String accessKey, Secret secretKey, boolean useRole, String endpoint, Destination dest, Map<String, String> userMetadata, String storageClass, String selregion, boolean useServerSideEncryption, ProxyConfiguration proxy) {
        super(accessKey, secretKey, useRole, endpoint, dest, userMetadata, storageClass, selregion, useServerSideEncryption, proxy);
    }

    // Return a File containing the gzipped contents of the input file.
    private File gzipFile(FilePath file) throws IOException, InterruptedException {
        final File localFile = File.createTempFile("s3plugin", ".bin");
        try (InputStream inputStream = file.read()) {
            try (OutputStream outputStream = new FileOutputStream(localFile)) {
                try (OutputStream gzipStream = new GZIPOutputStream(outputStream, true)) {
                    IOUtils.copy(inputStream, gzipStream);
                    gzipStream.flush();
                }
            }
        } catch (Exception ex) {
            localFile.delete();
            throw ex;
        }
        return localFile;
    }

    // Hook to ensure that the file is deleted once the upload finishes.
    private class CleanupHook implements ProgressListener {
        private final File localFile;

        CleanupHook(File localFile) {
            this.localFile = localFile;
        }

        @Override
        public void progressChanged(ProgressEvent event) {
            switch (event.getEventType()) {
            case TRANSFER_CANCELED_EVENT:
            case TRANSFER_COMPLETED_EVENT:
            case TRANSFER_FAILED_EVENT:
                localFile.delete();
            }
        }
    }

    @Override
    public String invoke(FilePath file) throws IOException, InterruptedException {
        final File localFile = gzipFile(file);
        Upload upload = null;

        try {
            final InputStream gzipedStream = new FileInputStream(localFile);
            final ObjectMetadata metadata = buildMetadata(file);
            metadata.setContentEncoding("gzip");
            metadata.setContentLength(localFile.length());

            upload = Uploads.getInstance().startUploading(getTransferManager(), file, gzipedStream, getDest().bucketName, getDest().objectName, metadata);

            String md5 = MD5.generateFromFile(localFile);

            // Add the cleanup hook only after we have the MD5,
            // because the hook might delete the file immediately.
            upload.addProgressListener(new CleanupHook(localFile));

            return md5;
        } finally {
            // The upload might have finished before we installed the progress listener.
            if (upload == null || upload.isDone()) {
                // The progress listener might have fired before this,
                // but .delete() on non-existent path is ok, and the
                // temporary name won't be reused by anything
                localFile.delete();
            }
        }
    }
}
