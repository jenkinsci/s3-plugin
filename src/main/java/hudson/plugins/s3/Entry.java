package hudson.plugins.s3;

import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;

public final class Entry {

    /**
     * options for x-amz-storage-class can be STANDARD or REDUCED_REDUNDANCY
     */
    public static final String[] STORAGE_CLASSES = {"STANDARD", "REDUCED_REDUNDANCY"};

    /**
     * Destination bucket for the copy. Can contain macros.
     */
    public String bucket;

    /**
     * File name relative to the workspace root to upload.
     * Can contain macros and wildcards.
     */
    public String sourceFile;

    /**
     * what x-amz-storage-class is currently set
     */
    public String storageClass = STORAGE_CLASSES[0];

    public String locationConstraint = Region.REGIONS[0].locationConstraint;

    @Nullable
    public Region getRegion() {
        for (Region region : Region.REGIONS) {
            if (StringUtils.equals(locationConstraint, region.locationConstraint))
                return region;
        }
        return null;
    }

}
