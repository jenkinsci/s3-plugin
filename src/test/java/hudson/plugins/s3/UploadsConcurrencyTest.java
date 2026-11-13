package hudson.plugins.s3;

import hudson.FilePath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadsConcurrencyTest {

    private static final int MAX_CONCURRENT_UPLOADS = 10;
    private static final int POOL_SIZE_EXCEEDING_UPLOADS = 60; // above the Netty client's default maxConcurrency of 50

    private ServerSocket stallingServer;
    private final List<Socket> heldConnections = new CopyOnWriteArrayList<>();
    private ExecutorService starter;

    @BeforeEach
    void startStallingServer() throws Exception {
        stallingServer = new ServerSocket(0);
        starter = Executors.newSingleThreadExecutor();
        final Thread acceptLoop = new Thread(() -> {
            while (!stallingServer.isClosed()) {
                try {
                    // Accept and hold the connection open without reading or writing,
                    // simulating slow in-flight uploads that never free their connection.
                    heldConnections.add(stallingServer.accept());
                } catch (Exception e) {
                    break;
                }
            }
        });
        acceptLoop.setDaemon(true);
        acceptLoop.start();
    }

    @AfterEach
    void stopStallingServer() throws Exception {
        starter.shutdownNow();
        stallingServer.close();
        for (Socket socket : heldConnections) {
            socket.close();
        }
    }

    @Test
    @Timeout(30)
    void manyUploadsShouldNotOpenMoreConnectionsThanTheConcurrencyLimit() throws Exception {
        final URI endpoint = URI.create("http://127.0.0.1:" + stallingServer.getLocalPort());
        final Uploads uploads = new Uploads(MAX_CONCURRENT_UPLOADS);

        try (S3AsyncClient client = ClientHelper.createAsyncClient(
                "key", "secret", false, "us-east-1", null, endpoint, null, true);
             S3TransferManager manager = S3TransferManager.builder().s3Client(client).build()) {

            starter.submit(() -> {
                IntStream.range(0, POOL_SIZE_EXCEEDING_UPLOADS).forEach(i -> startUpload(uploads, manager, i));
                return null;
            });

            awaitConnections(MAX_CONCURRENT_UPLOADS);
            // Give any upload beyond the limit the chance to connect before asserting it did not.
            TimeUnit.SECONDS.sleep(2);
            assertEquals(MAX_CONCURRENT_UPLOADS, heldConnections.size(),
                    "uploads beyond the concurrency limit must wait for a permit instead of opening connections");
        }
    }

    private void startUpload(Uploads uploads, S3TransferManager manager, int index) {
        try {
            final Uploads.Metadata metadata = new Uploads.Metadata(b -> { });
            metadata.setContentLength(4);
            uploads.startUploading(manager, new FilePath(new File("/tmp/uploads-concurrency-" + index)),
                    new ByteArrayInputStream(new byte[4]), "test-bucket", "file-" + index, metadata, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitConnections(int expected) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (heldConnections.size() < expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertTrue(heldConnections.size() >= expected,
                "expected " + expected + " connections but got " + heldConnections.size());
    }
}
