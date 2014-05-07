package hudson.plugins.s3;

import com.amazonaws.regions.Regions;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

public final class Entry  extends AbstractDescribableImpl <Entry> {
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
     * Upload either from the slave or the master
     */
    public boolean uploadFromSlave;

    /**
     * Let Jenkins manage the S3 uploaded artifacts
     */
    public boolean managedArtifacts;

    @DataBoundConstructor
    public Entry(String bucket, String sourceFile, String storageClass, String selectedRegion, boolean noUploadOnFailure,
                 boolean uploadFromSlave, boolean managedArtifacts) {
        this.bucket = bucket;
        this.sourceFile = sourceFile;
        this.storageClass = storageClass;
        this.selectedRegion = selectedRegion;
        this.noUploadOnFailure = noUploadOnFailure;
        this.uploadFromSlave = uploadFromSlave;
        this.managedArtifacts = managedArtifacts;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        public String getDisplayName() { return "S3 Upload File Entry"; }

        public ListBoxModel doFillStorageClassItems() {
            ListBoxModel items = new ListBoxModel();
            for (String storageClass : Entry.storageClasses) {
                items.add(storageClass);
            }
            return items;
        }

        public ListBoxModel doFillSelectedRegionItems() {
            ListBoxModel items = new ListBoxModel();
            for (Regions region : Entry.regions) {
                items.add(region.toString());
            }
            return items;
        }

    }
}
