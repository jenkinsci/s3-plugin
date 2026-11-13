package hudson.plugins.s3;

import hudson.FilePath;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadsTest {

    private Upload upload;
    private Uploads uploads;
    private FilePath file;

    @BeforeEach
    void setUp() {
        upload = mock(Upload.class);
        uploads = Uploads.getInstance();
        file = new FilePath(new File("/tmp/uploads-test-dummy.txt"));
    }

    @Test
    void finishUploading_succeedsWhenFutureCompletesNormally() {
        CompletedUpload completedUpload = mock(CompletedUpload.class);
        CompletableFuture<CompletedUpload> done = CompletableFuture.completedFuture(completedUpload);
        when(upload.completionFuture()).thenReturn(done);

        uploads.injectUpload(file, upload);
        assertDoesNotThrow(() -> uploads.finishUploading(file, 30));
    }

    @Test
    void finishUploading_throwsIOExceptionWhenUploadFails() {
        CompletableFuture<CompletedUpload> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("S3 connection reset"));
        when(upload.completionFuture()).thenReturn(failed);

        uploads.injectUpload(file, upload);
        IOException ex = assertThrows(IOException.class, () -> uploads.finishUploading(file, 15));
        assertTrue(ex.getMessage().contains("Upload failed for"));
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void finishUploading_cancelsExceptionallyOnTimeout(int timeout) throws ExecutionException, InterruptedException, TimeoutException {
        // A future that never completes with a simulated timeout
        @SuppressWarnings("unchecked")
        CompletableFuture<CompletedUpload> neverDone = mock(CompletableFuture.class);
        when(neverDone.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new TimeoutException("Simulated timeout"));
        when(neverDone.cancel(true)).thenReturn(true);
        when(upload.completionFuture()).thenReturn(neverDone);

        uploads.injectUpload(file, upload);
        int timeoutToCheckFor = Math.max(timeout, Uploads.MIN_UPLOAD_TIMEOUT);
        IOException ex = assertThrows(IOException.class, () -> uploads.finishUploading(file, timeout));
        assertTrue(ex.getMessage().contains("Upload timed out after " + timeoutToCheckFor + " minutes"));
        verify(neverDone).cancel(true);
    }

    @Test
    void startUploading_blocksUntilRunningUploadCompletes() throws Exception {
        final Uploads throttled = new Uploads(1);
        final S3TransferManager manager = mock(S3TransferManager.class);
        final CompletableFuture<CompletedUpload> firstDone = new CompletableFuture<>();
        final Upload first = mock(Upload.class);
        final Upload second = mock(Upload.class);
        when(first.completionFuture()).thenReturn(firstDone);
        when(second.completionFuture()).thenReturn(new CompletableFuture<>());
        when(manager.upload(any(UploadRequest.class))).thenReturn(first, second);

        throttled.startUploading(manager, file, new ByteArrayInputStream(new byte[0]), "bucket", "first", metadata(), null);

        final CompletableFuture<Upload> pending = CompletableFuture.supplyAsync(() -> {
            try {
                return throttled.startUploading(manager, file, new ByteArrayInputStream(new byte[0]), "bucket", "second", metadata(), null);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });

        assertThrows(TimeoutException.class, () -> pending.get(500, TimeUnit.MILLISECONDS));
        verify(manager, times(1)).upload(any(UploadRequest.class));

        firstDone.complete(mock(CompletedUpload.class));
        assertSame(second, pending.get(5, TimeUnit.SECONDS));
    }

    @Test
    void startUploading_releasesPermitWhenUploadFails() throws Exception {
        final Uploads throttled = new Uploads(1);
        final S3TransferManager manager = mock(S3TransferManager.class);
        final CompletableFuture<CompletedUpload> failed = new CompletableFuture<>();
        final Upload first = mock(Upload.class);
        final Upload second = mock(Upload.class);
        when(first.completionFuture()).thenReturn(failed);
        when(second.completionFuture()).thenReturn(new CompletableFuture<>());
        when(manager.upload(any(UploadRequest.class))).thenReturn(first, second);

        throttled.startUploading(manager, file, new ByteArrayInputStream(new byte[0]), "bucket", "first", metadata(), null);
        failed.completeExceptionally(new RuntimeException("boom"));

        assertSame(second, CompletableFuture.supplyAsync(() -> {
            try {
                return throttled.startUploading(manager, file, new ByteArrayInputStream(new byte[0]), "bucket", "second", metadata(), null);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }).get(5, TimeUnit.SECONDS));
    }

    @Test
    void startUploading_releasesPermitWhenManagerThrows() throws Exception {
        final Uploads throttled = new Uploads(1);
        final S3TransferManager manager = mock(S3TransferManager.class);
        final Upload second = mock(Upload.class);
        when(second.completionFuture()).thenReturn(new CompletableFuture<>());
        when(manager.upload(any(UploadRequest.class))).thenThrow(new IllegalStateException("boom")).thenReturn(second);

        assertThrows(IllegalStateException.class, () ->
                throttled.startUploading(manager, file, new ByteArrayInputStream(new byte[0]), "bucket", "first", metadata(), null));

        assertSame(second, throttled.startUploading(manager, file, new ByteArrayInputStream(new byte[0]), "bucket", "second", metadata(), null));
    }

    private static Uploads.Metadata metadata() {
        return new Uploads.Metadata(b -> { });
    }

    static Stream<Integer> timeouts() {
        return Stream.of(1, 10, 30);
    }
}
