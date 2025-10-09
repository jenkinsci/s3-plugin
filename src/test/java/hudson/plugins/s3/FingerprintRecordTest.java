package hudson.plugins.s3;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FingerprintRecordTest {

    @Test
    void testGetLinkFromWindowsPath() throws Exception {
        String windowsPath = "path\\to\\windows\\test.txt";
        FingerprintRecord windowsRecord = new FingerprintRecord(true, "test", windowsPath, "us-eat-1", "xxxx");
        String link = windowsRecord.getLink();
        String linkDecoded = URLDecoder.decode(link, StandardCharsets.UTF_8);
        assertNotEquals(windowsPath, link, "link is encoded");
        assertEquals(windowsPath, linkDecoded, "should match file name");
    }

    @Test
    void testGetLinkFromUnixPath() throws Exception {
        String unixPath = "/path/tmp/abc";
        FingerprintRecord unixRecord = new FingerprintRecord(true, "test", unixPath, "us-eat-1", "xxxx");
        assertEquals(unixPath, unixRecord.getLink(), "should match file name");
    }
}