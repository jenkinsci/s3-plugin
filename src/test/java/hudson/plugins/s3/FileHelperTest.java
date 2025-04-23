package hudson.plugins.s3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileHelperTest {

    @Test
    void testSelectedWithAsterisk() throws Exception {
        String includeFilter = "*";
        String excludeFilter = "b.txt";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
    }

    @Test
    void testSelectedSimpleCase() throws Exception {
        String includeFilter = "a.txt, c.txt";
        String excludeFilter = "b.txt, d.txt";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "c.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "d.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "e.txt"));
    }

    @Test
    void testSelectedComplexCase() throws Exception {
        String includeFilter = "a*.txt, c.txt";
        String excludeFilter = "*b.txt, d.txt";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "c.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "d.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "ab.txt"));
        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "abc.txt"));
        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "abd.txt"));
    }

    @Test
    void testSelectedWithEmptyExcludeFilter() throws Exception {
        String includeFilter = "a.txt";
        String excludeFilter = "";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
    }

    @Test
    void testSelectedWithEmptyIncludeFilter() throws Exception {
        String includeFilter = "";
        String excludeFilter = "b.txt";

        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
    }

    @Test
    void testSelectedWithNullIncludeFilter() throws Exception {
        String excludeFilter = "";

        assertFalse(FileHelper.selected(null, excludeFilter, "a.txt"));
    }

    @Test
    void testSelectedWithNullExcludeFilter() throws Exception {
        String includeFilter = "a.txt";

        assertTrue(FileHelper.selected(includeFilter, null, "a.txt"));
    }

    @Test
    void testGetStartIndexWithAsterisk() throws Exception {
        String workspace = "/var/lib/jenkins/jobs/workspace";
        String folder = "tests/*";

        int startIndex = FileHelper.getSearchPathLength(workspace, folder, false);
        assertEquals("/var/lib/jenkins/jobs/workspace/tests/".length(), startIndex);
    }

    @Test
    void testGetStartIndexWithAsteriskInside() throws Exception {
        final String workspace = "/var/lib/jenkins/jobs/workspace";
        final String folder = "tests/*/folder";

        final String shouldBeCut = "/var/lib/jenkins/jobs/workspace/tests/";

        int startIndex = FileHelper.getSearchPathLength(workspace, folder, false);
        assertEquals(shouldBeCut.length(), startIndex);
    }

    @Test
    void testGetStartIndexWithoutAsterisk() throws Exception {
        String workspace = "/var/lib/jenkins/jobs/workspace";
        String folder = "tests/";

        final String shouldBeCut = "/var/lib/jenkins/jobs/workspace/";

        int startIndex = FileHelper.getSearchPathLength(workspace, folder, false);
        assertEquals(shouldBeCut.length(), startIndex);
    }

    @Test
    void testGetStartIndexWithAsteriskButKeepStructure() throws Exception {
        String workspace = "/var/lib/jenkins/jobs/workspace";
        String folder = "tests/*";

        final String shouldBeCut = "/var/lib/jenkins/jobs/workspace/";

        int startIndex = FileHelper.getSearchPathLength(workspace, folder, true);
        assertEquals(shouldBeCut.length(), startIndex);
    }

    @Test
    void testGetStartIndexWithAsteriskInTheMiddle() throws Exception {
        String workspace = "/var/lib/jenkins/jobs/workspace";
        String folder = "tests/some_name.*.0.extension";

        final String shouldBeCut = "/var/lib/jenkins/jobs/workspace/tests/";

        int startIndex = FileHelper.getSearchPathLength(workspace, folder, false);
        assertEquals(shouldBeCut.length(), startIndex);
    }

    @Test
    void testGetStartIndexWithAsteriskInsideButKeepStructure() throws Exception {
        String workspace = "/var/lib/jenkins/jobs/workspace";
        String folder = "tests/*/folder";

        final String shouldBeCut = "/var/lib/jenkins/jobs/workspace/";

        int startIndex = FileHelper.getSearchPathLength(workspace, folder, true);
        assertEquals(shouldBeCut.length(), startIndex);
    }
}