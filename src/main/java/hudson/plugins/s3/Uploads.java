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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class Uploads {
    private Uploads() {
        this(MAX_CONCURRENT_UPLOADS);
    }

    Uploads(int maxConcurrentUploads) {
        this(maxConcurrentUploads, UPLOAD_SLOT_TIMEOUT, TimeUnit.MINUTES);
    }

    Uploads(int maxConcurrentUploads, long slotTimeout, TimeUnit slotTimeoutUnit) {
        // Fair, because the wait below is now bounded: this instance is a singleton shared by
        // every concurrent build on the node, so an unfair semaphore lets a busy build barge
        // ahead repeatedly and time out a waiting one even while slots keep turning over.
        this.uploadPermits = new Semaphore(maxConcurrentUploads, true);
        this.maxConcurrentUploads = maxConcurrentUploads;
        this.slotTimeout = slotTimeout;
        this.slotTimeoutUnit = slotTimeoutUnit;
    }

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
    public static final int DEFAULT_MAX_CONCURRENT_UPLOADS = 10;
    /**
     * Upper bound of transfers in flight at once, to keep the HTTP connection pool from being exhausted.
     * Override with {@code -Dhudson.plugins.s3.Uploads.maxConcurrentUploads=N}; read in the JVM doing the upload.
     */
    public static final int MAX_CONCURRENT_UPLOADS = Math.max(1,
            Integer.getInteger(Uploads.class.getName() + ".maxConcurrentUploads", DEFAULT_MAX_CONCURRENT_UPLOADS));
    public static final int DEFAULT_UPLOAD_TIMEOUT = 30;
    public static final int MIN_UPLOAD_TIMEOUT = 5;

    public static final int DEFAULT_UPLOAD_SLOT_TIMEOUT = 30;
    /**
     * How long to wait for a free upload slot before failing the file, in minutes. Bounds the wait
     * introduced by {@link #MAX_CONCURRENT_UPLOADS} so that uploads which never complete cannot
     * block the caller indefinitely with no output. Override with
     * {@code -Dhudson.plugins.s3.Uploads.uploadSlotTimeoutMinutes=N}; read in the JVM doing the upload.
     */
    public static final int UPLOAD_SLOT_TIMEOUT = Math.max(1,
            Integer.getInteger(Uploads.class.getName() + ".uploadSlotTimeoutMinutes", DEFAULT_UPLOAD_SLOT_TIMEOUT));

    private final transient Map<FilePath, InputStream> openedStreams = new ConcurrentHashMap<>();
    private final transient Semaphore uploadPermits;
    private final transient int maxConcurrentUploads;
    private final transient long slotTimeout;
    private final transient TimeUnit slotTimeoutUnit;

    /**
     * Thrown when no upload slot became free in time. Distinct from a failed upload because a
     * retry cannot help: the slots are held by transfers that are not completing.
     */
    public static class UploadSlotTimeoutException extends IOException {
        private static final long serialVersionUID = 1L;

        public UploadSlotTimeoutException(String message) {
            super(message);
        }
    }

    public Upload startUploading(S3TransferManager manager, FilePath file, InputStream inputStream, String bucketName, String objectName, Metadata metadata, TransferListener listener) throws InterruptedException, IOException {
        UploadRequest.Builder request = UploadRequest.builder();
        request.putObjectRequest(metadata.builder.andThen(b -> b.bucket(bucketName).key(objectName).metadata(metadata.metadata)));
        request.requestBody(AsyncRequestBody.fromInputStream(inputStream, metadata.getContentLength(), executors));

        if (listener != null) {
            request.addTransferListener(listener);
        }
        // Bounded, not acquire(): a transfer whose completion future never fires would otherwise
        // hold its permit forever, blocking here -- before waitUploads, which owns the only
        // upload timeout -- so the build would hang with no exception and no log line.
        // Until the stream is recorded in openedStreams it is this method's to close, so every
        // path that leaves without starting a transfer has to close it.
        final boolean acquired;
        try {
            acquired = uploadPermits.tryAcquire(slotTimeout, slotTimeoutUnit);
        } catch (InterruptedException e) {
            closeUnstarted(inputStream, e);
            throw e;
        }
        if (!acquired) {
            final UploadSlotTimeoutException timedOut = new UploadSlotTimeoutException("Timed out after "
                    + slotTimeout + " " + slotTimeoutUnit.toString().toLowerCase(Locale.ROOT)
                    + " waiting for a free upload slot for: " + file.getName()
                    + ". The upload slot limit is " + maxConcurrentUploads
                    + " and no slot became available. Raise -D" + Uploads.class.getName()
                    + ".maxConcurrentUploads or -D" + Uploads.class.getName() + ".uploadSlotTimeoutMinutes.");
            closeUnstarted(inputStream, timedOut);
            throw timedOut;
        }
        final Upload upload;
        try {
            upload = manager.upload(request.build());
        } catch (RuntimeException e) {
            uploadPermits.release();
            closeUnstarted(inputStream, e);
            throw e;
        }
        upload.completionFuture().whenComplete((completed, failure) -> uploadPermits.release());
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
        final Upload upload = startedUploads.remove(filePath);
        if (upload != null) {
            // Cancelling completes the future, which runs the whenComplete hook in startUploading
            // and releases the permit; without it an abandoned upload leaks one permanently.
            upload.completionFuture().cancel(true);
        }
        closeStream(filePath);
    }

    /**
     * Closes a stream for an upload that never reached the transfer manager, so it was never
     * recorded in {@code openedStreams} and nothing else will close it.
     */
    private static void closeUnstarted(InputStream inputStream, Throwable cause) {
        try {
            inputStream.close();
        } catch (IOException e) {
            cause.addSuppressed(e);
        }
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
