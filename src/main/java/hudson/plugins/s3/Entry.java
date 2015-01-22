package hudson.plugins.s3;

import com.amazonaws.regions.Regions;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

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
     * Artifact management options. Managed by Jenkins or by owner.
     */
    public enum managedArtifactsEnum {
        UNMANAGED_FLATTENED,
        UNMANAGED_STRUCTURED,
        MANAGED_FLATTENED,
        MANAGED_STRUCTURED
    }

    /**
     * Currently selected artifact management style
     */
    public String artifactManagement;

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
                 boolean noUploadOnFailure, boolean uploadFromSlave, String artifactManagement,
                 boolean managedArtifacts, boolean useServerSideEncryption, boolean flatten) {
        this.bucket = bucket;
        this.sourceFile = sourceFile;
        this.storageClass = storageClass;
        this.selectedRegion = selectedRegion;
        this.noUploadOnFailure = noUploadOnFailure;
        this.uploadFromSlave = uploadFromSlave;
        this.artifactManagement = artifactManagement;
        this.useServerSideEncryption = useServerSideEncryption;
    }

    public boolean isManaged() {
        return isManaged(artifactManagement);
    }

    public static boolean isManaged(final String management) {
        return ( managedArtifactsEnum.MANAGED_FLATTENED.name().equals(management) ||
                managedArtifactsEnum.MANAGED_STRUCTURED.name().equals(management) );
    }

    public boolean isStructured() {
        return isStructured(artifactManagement);
    }

    public static boolean isStructured(final String management) {
        return ( managedArtifactsEnum.UNMANAGED_STRUCTURED.name().equals(management) ||
                managedArtifactsEnum.MANAGED_STRUCTURED.name().equals(management) );
    }

    public Descriptor<Entry> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public final static DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends  Descriptor<Entry> {

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

        public ListBoxModel doFillArtifactManagementItems() {
            ListBoxModel model = new ListBoxModel();
            for (managedArtifactsEnum a : managedArtifactsEnum.values()) {
                model.add(a.name(), a.name());
            }
            return model;
        }
    };

}
