package hudson.plugins.s3;

import org.junit.Test;

import static org.junit.Assert.*;

public class FileHelperTest {
    @Test
    public void testSelectedWithAsterisk() throws Exception {
        String includeFilter = "*";
        String excludeFilter = "b.txt";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
    }

    @Test
    public void testSelectedSimpleCase() throws Exception {
        String includeFilter = "a.txt, c.txt";
        String excludeFilter = "b.txt, d.txt";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "c.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "d.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "e.txt"));
    }

    @Test
    public void testSelectedComplexCase() throws Exception {
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
    public void testSelectedWithEmptyExcludeFilter() throws Exception {
        String includeFilter = "a.txt";
        String excludeFilter = "";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertFalse(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
    }

    @Test
    public void testSelectedWithEmptyIncludeFilter() throws Exception {
        String includeFilter = "*";
        String excludeFilter = "";

        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "a.txt"));
        assertTrue(FileHelper.selected(includeFilter, excludeFilter, "b.txt"));
    }
}