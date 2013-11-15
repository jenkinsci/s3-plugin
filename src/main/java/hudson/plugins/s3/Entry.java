package hudson.plugins.s3;

public final class Entry {
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
    public String storageClass;
    /**
     * Stores the Region Value
     */
    public String selectedRegion;
}
