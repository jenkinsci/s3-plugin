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
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.Upload;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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

    static Stream<Integer> timeouts() {
        return Stream.of(1, 10, 30);
    }
}
