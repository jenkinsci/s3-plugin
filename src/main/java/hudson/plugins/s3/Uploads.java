package hudson.plugins.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Logger;

public final class Uploads {
    private Uploads() {}
    private static final Logger LOGGER = Logger.getLogger(Uploads.class.getName());
    private static final int MULTIPART_UPLOAD_THRESHOLD = 16*1024*1024; // 16 MB

    private static transient volatile Uploads instance;
    private final transient HashMap<FilePath, Upload> startedUploads = new HashMap<>();
    private final transient HashMap<FilePath, InputStream> openedStreams = new HashMap<>();

    public Upload startUploading(TransferManager manager, FilePath file, InputStream inputsStream, String bucketName, String objectName, ObjectMetadata metadata) throws AmazonServiceException {
        final PutObjectRequest request = new PutObjectRequest(bucketName, objectName, inputsStream, metadata);

        // Set the buffer size (ReadLimit) equal to the multipart upload size,
        // allowing us to resend data if the connection breaks.
        request.getRequestClientOptions().setReadLimit(MULTIPART_UPLOAD_THRESHOLD);
        manager.getConfiguration().setMultipartUploadThreshold( (long) MULTIPART_UPLOAD_THRESHOLD);

        final Upload upload = manager.upload(request);
        startedUploads.put(file, upload);
        openedStreams.put(file, inputsStream);
        return upload;
    }

    public void finishUploading(FilePath filePath) throws InterruptedException {
        final Upload upload = startedUploads.remove(filePath);
        if (upload == null) {
            LOGGER.info("File: " + filePath.getName() + " already was uploaded");
            return;
        }
        try {
            upload.waitForCompletion();
        }
        finally {
            closeStream(filePath);
        }
    }

    public void cleanup(FilePath filePath) {
        startedUploads.remove(filePath);
        closeStream(filePath);
    }

    private void closeStream(FilePath filePath) {
        try {
            final InputStream stream = openedStreams.remove(filePath);
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to close stream for file:" + filePath);
        }
    }

    public static Uploads getInstance() {
        if (instance == null) {
            synchronized (Uploads.class) {
                if (instance == null) {
                    instance = new Uploads();
                }
            }
        }
        return instance;
    }
}
