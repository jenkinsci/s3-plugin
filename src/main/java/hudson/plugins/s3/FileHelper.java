package hudson.plugins.s3;

import org.apache.tools.ant.types.selectors.FilenameSelector;

import java.io.File;

public class FileHelper {
    public static boolean selected(String includeFilter, String excludeFilter, String filename) {
        final String[] includeFilters = includeFilter.split(",");
        final String[] excludeFilters = excludeFilter.split(",");
        final FilenameSelector positiveSelector = new FilenameSelector();
        final FilenameSelector negativeSelector = new FilenameSelector();

        for (String exclude : excludeFilters) {
            negativeSelector.setName(exclude.trim());

            if (negativeSelector.isSelected(new File("/"), filename, null)) {
                return false;
            }
        }

        for (String include : includeFilters) {
            positiveSelector.setName(include.trim());

            if (positiveSelector.isSelected(new File("/"), filename, null))
                return true;
        }

        return false;
    }
}
