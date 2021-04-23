package hudson.plugins.s3;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.AmazonS3;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

public final class Entry implements Describable<Entry> {

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
     * File name relative to the workspace root to be excluded from upload.
     * Can contain macros and wildcards.
     */
    public String excludedFile;
    /**
     * options for x-amz-storage-class can be STANDARD, STANDARD_IA, or REDUCED_REDUNDANCY
     */
    static final String[] storageClasses = {"STANDARD", "STANDARD_IA", "REDUCED_REDUNDANCY"};
    /**
     * what x-amz-storage-class is currently set
     */
    public String storageClass;
    /**
     * Regions Values
     */
    public static final List<Region> regions = RegionUtils.getRegionsForService(AmazonS3.ENDPOINT_PREFIX);
    /**
     * Stores the Region Value
     */
    public String selectedRegion;
    
    /**
     * Do not publish the artifacts when build fails
     */
    public boolean noUploadOnFailure;

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

    /**
    * use GZIP to compress files
    */
    public boolean gzipFiles;

    /**
     * show content of entity directly in browser
     */
    public boolean showDirectlyInBrowser;

    /**
     * Don't delete artifacts in Amazon after job was rotated
     */

    public boolean keepForever;

    /**
    * Metadata overrides
    */
    public List<MetadataPair> userMetadata;
    
    public boolean injectUrl; 
    
      
    public String buildVariable;
    
    
    public boolean preSignedUrl;
    
    
    public Long signedUrlExpirySeconds;
    
//    /**
//     * Indicates whether the S3 pre-signed URLs should be generated
//     */
//    public boolean preSignedUrl;
//    
//    /**
//     * This is an expiration time for pre-signed URLs 
//     */
//    public long signedUrlExpirySeconds;

    /**
     * @param bucket
     * @param sourceFile
     * @param excludedFile
     * @param storageClass
     * @param selectedRegion
     * @param noUploadOnFailure
     * @param uploadFromSlave
     * @param managedArtifacts
     * @param useServerSideEncryption
     * @param flatten
     * @param gzipFiles
     * @param keepForever
     * @param showDirectlyInBrowser
     * @param injectPresignedUrl
     * @param preSignedUrl
     * @param signedUrlExpirySeconds
     * @param userMetadata
     */
    @DataBoundConstructor
    public Entry(String bucket, String sourceFile, String excludedFile, String storageClass, String selectedRegion,
                 boolean noUploadOnFailure, boolean uploadFromSlave, boolean managedArtifacts,
                 boolean useServerSideEncryption, boolean flatten, boolean gzipFiles, boolean keepForever,
                 boolean showDirectlyInBrowser, boolean injectUrl, String buildVariable, boolean preSignedUrl, Long signedUrlExpirySeconds, List<MetadataPair> userMetadata) {
        this.bucket = bucket;
        this.sourceFile = sourceFile;
        this.excludedFile = excludedFile;
        this.storageClass = storageClass;
        this.selectedRegion = selectedRegion;
        this.noUploadOnFailure = noUploadOnFailure;
        this.uploadFromSlave = uploadFromSlave;
        this.managedArtifacts = managedArtifacts;
        this.useServerSideEncryption = useServerSideEncryption;
        this.flatten = flatten;
        this.gzipFiles = gzipFiles;
        this.keepForever = keepForever;
        this.showDirectlyInBrowser = showDirectlyInBrowser;
        this.injectUrl = injectUrl;
        this.buildVariable = buildVariable;
        this.preSignedUrl = preSignedUrl;
        this.signedUrlExpirySeconds = signedUrlExpirySeconds;
        this.userMetadata = userMetadata;
    }
    
    @Override
    public Descriptor<Entry> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends  Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "File to upload";
        }

        public ListBoxModel doFillStorageClassItems() {
            final ListBoxModel model = new ListBoxModel();
            for (String s : storageClasses) {
                model.add(s, s);
            }
            return model;
        }

        public ListBoxModel doFillSelectedRegionItems() {
            final ListBoxModel model = new ListBoxModel();
            for (Region r : regions) {
                model.add(r.getName(), r.getName());
            }
            return model;
        }
    }

}
