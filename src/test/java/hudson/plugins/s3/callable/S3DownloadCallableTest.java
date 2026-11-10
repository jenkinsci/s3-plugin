package hudson.plugins.s3.callable;

import com.sun.net.httpserver.HttpServer;
import hudson.plugins.s3.Destination;
import hudson.util.Secret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3DownloadCallableTest {

    private HttpServer fakeS3;

    @BeforeEach
    void startFakeS3() throws IOException {
        fakeS3 = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeS3.createContext("/", exchange -> {
            byte[] body = "Hello World".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("ETag", "\"deadbeef\"");
            exchange.getResponseHeaders().add("Content-Range", "bytes 0-" + (body.length - 1) + "/" + body.length);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeS3.start();
        System.setProperty("hudson.plugins.s3.ENDPOINT", "http://localhost:" + fakeS3.getAddress().getPort());
    }

    @AfterEach
    void stopFakeS3() {
        fakeS3.stop(0);
        System.clearProperty("hudson.plugins.s3.ENDPOINT");
    }

    @Test
    void invokeCreatesMissingDestinationParentDirectory(@TempDir Path workspace) throws IOException {
        final Destination dest = new Destination("bucket", "test.txt");
        final File destination = workspace.resolve("artifact").resolve("images").resolve("test.txt").toFile();
        assertTrue(!destination.getParentFile().exists());

        final S3DownloadCallable callable = new S3DownloadCallable(
                "access-key", Secret.fromString("secret-key"), false, dest, "us-east-1", null, true);

        assertDoesNotThrow(() -> callable.invoke(destination, null));

        assertEquals("Hello World", Files.readString(destination.toPath(), StandardCharsets.UTF_8));
    }
}
