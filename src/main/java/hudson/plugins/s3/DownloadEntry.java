package hudson.plugins.s3;

import com.amazonaws.regions.Regions;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public final class DownloadEntry implements Describable<DownloadEntry> {

    /**
     * Target directory for the copy. Can contain macros.
     */
    public String bucket;
    /**
     * Target directory for the copy. Can contain macros.
     */
    public String target;
    /**
     * Can contain macros and wildcards.
     */
    public String includedPattern;
    /**
     * Can contain macros and wildcards.
     */
    public String excludedPattern;

    /**
     * Flatten directories
     */
    public boolean flatten;

    /**
     * Regions Values
     */
    public static final Regions[] regions = Regions.values();
    /**
     * Stores the Region Value
     */
    public String selectedRegion;

    @DataBoundConstructor
    public DownloadEntry(String bucket, String target, String includedPattern, String excludedPattern, String selectedRegion, boolean flatten) {
        this.bucket = bucket;
        this.target = target;
        this.includedPattern = includedPattern;
        this.excludedPattern = excludedPattern;
        this.selectedRegion = selectedRegion;
        this.flatten = flatten;
    }

    @Override
    public Descriptor<DownloadEntry> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends  Descriptor<DownloadEntry> {

        @Override
        public String getDisplayName() {
            return "Artifact to copy";
        }
    }

}
