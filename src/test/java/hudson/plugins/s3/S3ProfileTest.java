package hudson.plugins.s3;

import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3ProfileTest {

    private final S3Profile profile = new S3Profile("test", "key", "secret", false, 0, "1", "1", "1", "1", true);
    private final FilePath targetDir = new FilePath(new File(System.getProperty("java.io.tmpdir"), "s3-profile-test"));

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Run<?, ?> mockRun() {
        Job job = mock(Job.class);
        when(job.getFullName()).thenReturn("project");
        when(job.getName()).thenReturn("project");
        Run run = mock(Run.class);
        when(run.getParent()).thenReturn(job);
        when(run.getNumber()).thenReturn(1);
        return run;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "..\\..\\etc\\passwd",
            "foo/../../bar",
            "..",
            "/etc/passwd",
            "C:\\evil\\payload.txt",
            "C:/evil/payload.txt",
            "C:evil\\payload.txt",
            "\\\\server\\share\\evil.txt",
    })
    void getFilePathRejectsNamesThatEscapeTargetDirectory(String maliciousName) {
        IOException ex = assertThrows(IOException.class,
                () -> profile.getFilePath(targetDir, false, maliciousName));
        assertTrue(ex.getMessage().contains("escapes target directory"));
    }

    @Test
    void getFilePathAcceptsLegitimateNestedNames() throws IOException {
        FilePath result = profile.getFilePath(targetDir, false, "artifact/images/x.tar.bz2");
        assertEquals(new FilePath(targetDir, "artifact/images/x.tar.bz2").getRemote(), result.getRemote());
    }

    @Test
    void getFilePathFlattensAndStripsDirectoriesRegardlessOfTraversalAttempts() throws IOException {
        FilePath result = profile.getFilePath(targetDir, true, "../../etc/passwd");
        assertEquals(new FilePath(targetDir, "passwd").getRemote(), result.getRemote());
    }

    @Test
    void downloadAllRejectsArtifactNamesThatEscapeTargetDirectory() {
        List<FingerprintRecord> artifacts = List.of(
                new FingerprintRecord(true, "bucket", "C:\\evil\\payload.txt", "us-east-1", "deadbeef"));

        IOException ex = assertThrows(IOException.class,
                () -> profile.downloadAll(mockRun(), artifacts, "*.txt", "", targetDir, false));
        assertTrue(ex.getMessage().contains("escapes target directory"));
    }

    @Test
    void downloadAllIgnoresEscapingArtifactNamesThatAreNotSelectedByTheFilter() throws IOException, InterruptedException {
        List<FingerprintRecord> artifacts = List.of(
                new FingerprintRecord(true, "bucket", "C:\\evil\\payload.txt", "us-east-1", "deadbeef"));

        List<FingerprintRecord> result = profile.downloadAll(mockRun(), artifacts, "*.zip", "", targetDir, false);

        assertTrue(result.isEmpty());
    }
}
