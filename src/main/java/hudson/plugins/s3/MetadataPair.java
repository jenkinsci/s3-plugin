package hudson.plugins.s3;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public final class MetadataPair implements Describable<MetadataPair> {

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

    public Descriptor<MetadataPair> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public final static DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<MetadataPair> {

        @Override
        public String getDisplayName() {
            return "Metadata";
        }
    };
}
