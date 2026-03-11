package hudson.plugins.s3;

import hudson.FilePath;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;
import software.amazon.awssdk.utils.NamedThreadFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class Uploads {
    private Uploads() {}
    private static final Logger LOGGER = Logger.getLogger(Uploads.class.getName());
    public static final int MULTIPART_UPLOAD_THRESHOLD = 16*1024*1024; // 16 MB

    private static transient volatile Uploads instance;

    private final transient Map<FilePath, Upload> startedUploads = new ConcurrentHashMap<>();
    private final ExecutorService executors;
    // This creates a cached thread pool with an upper bound (5) on threads to be spawned on demand.
    // TODO: S3 client could use Virtual threads when baseline is Java 21
    {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            5, 5,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new NamedThreadFactory(Executors.defaultThreadFactory(), Uploads.class.getName())
        );
        pool.allowCoreThreadTimeOut(true);
        executors = pool;
    }
    public static final int DEFAULT_UPLOAD_TIMEOUT = 30;
    public static final int MIN_UPLOAD_TIMEOUT = 5;

    private final transient Map<FilePath, InputStream> openedStreams = new ConcurrentHashMap<>();

    public Upload startUploading(S3TransferManager manager, FilePath file, InputStream inputStream, String bucketName, String objectName, Metadata metadata, TransferListener listener) {
        UploadRequest.Builder request = UploadRequest.builder();
        request.putObjectRequest(metadata.builder.andThen(b -> b.bucket(bucketName).key(objectName).metadata(metadata.metadata)));
        request.requestBody(AsyncRequestBody.fromInputStream(inputStream, metadata.getContentLength(), executors));

        if (listener != null) {
            request.addTransferListener(listener);
        }
        final Upload upload = manager.upload(request.build());
        startedUploads.put(file, upload);
        openedStreams.put(file, inputStream);
        return upload;
    }

    public void finishUploading(FilePath filePath) throws InterruptedException, IOException {
        finishUploading(filePath, DEFAULT_UPLOAD_TIMEOUT);
    }

    public void finishUploading(FilePath filePath, int uploadTimeout) throws InterruptedException, IOException {
        int effectiveTimeout = Math.max(uploadTimeout, MIN_UPLOAD_TIMEOUT);
        final Upload upload = startedUploads.remove(filePath);
        if (upload == null) {
            LOGGER.info("File: " + filePath.getName() + " already was uploaded");
            return;
        }
        try {
            upload.completionFuture().get(effectiveTimeout, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            upload.completionFuture().cancel(true); // cancel the upload
            throw e;
        } catch (ExecutionException e) {
            throw new IOException("Upload failed for: " + filePath.getName(), e.getCause());
        } catch (TimeoutException e) {
            upload.completionFuture().cancel(true);
            throw new IOException("Upload timed out after " + effectiveTimeout + " minutes for: " + filePath.getName(), e);
        } finally {
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

    void injectUpload(FilePath file, Upload upload) {
        startedUploads.put(file, upload);
    }

    public static class Metadata {
        private Consumer<PutObjectRequest.Builder> builder;
        private final Map<String, String> metadata;
        private long contentLength;
        public Metadata(Consumer<PutObjectRequest.Builder> builder, Map<String, String> metadata) {
            this.builder = builder;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }

        public Metadata(Consumer<PutObjectRequest.Builder> builder) {
            this(builder, new HashMap<>());
        }

        public void putMetadata(String key, String value) {
            metadata.put(key, value);
        }

        public long getContentLength() {
            return contentLength;
        }

        public void setContentLength(long contentLength) {
            this.contentLength = contentLength;
        }

        public void andThen(Consumer<PutObjectRequest.Builder> addition) {
            builder = builder.andThen(addition);
        }
    }
}
