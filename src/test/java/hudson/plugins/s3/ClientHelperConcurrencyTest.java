package hudson.plugins.s3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientHelperConcurrencyTest {

    private ServerSocket stallingServer;
    private Thread acceptLoop;

    @BeforeEach
    void startStallingServer() throws Exception {
        stallingServer = new ServerSocket(0);
        acceptLoop = new Thread(() -> {
            while (!stallingServer.isClosed()) {
                try {
                    // Accept and hold the connection open without reading or writing,
                    // simulating multiple large, slow in-flight uploads that never
                    // free their connection within the pool's acquisition window.
                    Socket socket = stallingServer.accept();
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
        stallingServer.close();
    }

    @Test
    @Timeout(30)
    void manyConcurrentUploadsShouldNotExhaustTheConnectionPool() throws Exception {
        URI endpoint = URI.create("http://127.0.0.1:" + stallingServer.getLocalPort());

        try (S3AsyncClient client = ClientHelper.createAsyncClient(
                "key", "secret", false, "us-east-1", null, endpoint, null, true)) {

            int concurrentUploads = 60; // exceeds the Netty client's default maxConcurrency of 50
            CountDownLatch acquireTimeoutObserved = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            for (int i = 0; i < concurrentUploads; i++) {
                final int fileIndex = i;
                client.headObject(b -> b.bucket("test-bucket").key("file-" + fileIndex))
                        .whenComplete((response, error) -> {
                            if (error != null && mentionsAcquireTimeout(error)) {
                                failure.set(error);
                                acquireTimeoutObserved.countDown();
                            }
                        });
            }

            boolean observed = acquireTimeoutObserved.await(20, TimeUnit.SECONDS);
            assertFalse(observed,
                    "ClientHelper builds the async HTTP client with no maxConcurrency/connectionAcquisitionTimeout" +
                            " override, so requests queued beyond the default pool size of 50 fail with: " + failure.get());
        }
    }

    private static boolean mentionsAcquireTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("Acquire operation took longer")) {
                return true;
            }
        }
        return false;
    }
}
