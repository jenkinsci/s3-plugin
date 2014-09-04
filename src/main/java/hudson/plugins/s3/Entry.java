package hudson.plugins.s3;

import com.amazonaws.regions.Regions;

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
     * options for x-amz-storage-class can be STANDARD or REDUCED_REDUNDANCY
     */
    public static final String[] storageClasses = {"STANDARD", "REDUCED_REDUNDANCY"};
    /**
     * what x-amz-storage-class is currently set
     */
    public String storageClass;
    /**
     * Regions Values
     */
    public static final Regions[] regions = Regions.values();
    /**
     * Stores the Region Value
     */
    public String selectedRegion;
    
    /**
     * Do not publish the artifacts when build fails
     */
    public boolean noUploadOnFailure;

    /**
     * Fail the build if no files were uploaded.
     */
    public boolean failNoFilesToUpload;

    /**
     * Upload either from the slave or the master
     */
    public boolean uploadFromSlave;

    /**
     * Let Jenkins manage the S3 uploaded artifacts
     */
    public boolean managedArtifacts;
    
    /**
     * Use S3 server side encryption when uploading the artifacts
     */
    public boolean useServerSideEncryption;

    /**
     * Flatten directories
     */
    public boolean flatten;
}
