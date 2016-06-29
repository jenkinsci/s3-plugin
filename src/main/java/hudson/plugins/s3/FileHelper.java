package hudson.plugins.s3;

import org.apache.tools.ant.types.selectors.FilenameSelector;

import java.io.File;

public class FileHelper {
    public static boolean selected(String includeFilter, String excludeFilter, String filename) {
        if (includeFilter == null) {
            return false;
        }

        final FilenameSelector positiveSelector = new FilenameSelector();
        final FilenameSelector negativeSelector = new FilenameSelector();

        if (excludeFilter != null) {
            final String[] excludeFilters = excludeFilter.split(",");

            for (String exclude : excludeFilters) {
                negativeSelector.setName(exclude.trim());

                if (negativeSelector.isSelected(new File("/"), filename, null)) {
                    return false;
                }
            }
        }

        final String[] includeFilters = includeFilter.split(",");

        for (String include : includeFilters) {
            positiveSelector.setName(include.trim());

            if (positiveSelector.isSelected(new File("/"), filename, null))
                return true;
        }

        return false;
    }

    public static int getSearchPathLength(String workSpace, String filterExpanded, boolean alwaysKeepParentDirectory) {
        if (alwaysKeepParentDirectory) {
            return workSpace.length() + 1;
        }

        final File file1 = new File(workSpace);
        final File file2 = new File(file1, filterExpanded);

        final String pathWithFilter = file2.getPath();

        final int indexOfWildCard = pathWithFilter.indexOf('*');


        if (indexOfWildCard > 0) {
            final String s = pathWithFilter.substring(0, indexOfWildCard);
            return s.length();
        } else {
            return file2.getParent().length() + 1;
        }
    }
}