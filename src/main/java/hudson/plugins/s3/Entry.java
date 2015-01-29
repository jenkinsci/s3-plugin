package hudson.plugins.s3;

import com.amazonaws.regions.Regions;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

public final class Entry implements Describable<Entry> {

    /**
     * options for x-amz-storage-class can be STANDARD or REDUCED_REDUNDANCY
     */
    public static final String[] storageClasses = {"STANDARD", "REDUCED_REDUNDANCY"};
    /**
     * Regions Values
     */
    public static final Regions[] regions = Regions.values();
    @Extension
    public final static DescriptorImpl DESCRIPOR = new DescriptorImpl();
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

    @DataBoundConstructor
    public Entry(String bucket, String sourceFile, String storageClass, String selectedRegion,
                 boolean noUploadOnFailure, boolean uploadFromSlave, boolean managedArtifacts,
                 boolean useServerSideEncryption, boolean flatten) {
        this.bucket = bucket;
        this.sourceFile = sourceFile;
        this.storageClass = storageClass;
        this.selectedRegion = selectedRegion;
        this.noUploadOnFailure = noUploadOnFailure;
        this.uploadFromSlave = uploadFromSlave;
        this.managedArtifacts = managedArtifacts;
        this.useServerSideEncryption = useServerSideEncryption;
        this.flatten = flatten;
    }

    public Descriptor<Entry> getDescriptor() {
        return DESCRIPOR;
    }

    public static class DescriptorImpl extends Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "File to upload";
        }

        public ListBoxModel doFillStorageClassItems() {
            ListBoxModel model = new ListBoxModel();
            for (String s : storageClasses) {
                model.add(s, s);
            }
            return model;
        }

        public ListBoxModel doFillSelectedRegionItems() {
            ListBoxModel model = new ListBoxModel();
            for (Regions r : regions) {
                model.add(r.getName(), r.getName());
            }
            return model;
        }

    }

}
