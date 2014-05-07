package hudson.plugins.s3;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public final class MetadataPair extends AbstractDescribableImpl<MetadataPair> {
    /**
     * The key of the user metadata pair to tag an upload with.
     * Can contain macros.
     */
    public String key;
    /**
     * The key of the user metadata pair to tag an upload with.
     * Can contain macros.
     */
    public String value;

    @DataBoundConstructor
    public MetadataPair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MetadataPair> {
        public String getDisplayName() { return "S3 Metadata pair"; }
    }
}
