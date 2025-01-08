package hudson.plugins.s3;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public final class TagPair implements Describable<TagPair> {

    /**
     * The key of the user tag pair to tag an upload with.
     * Can contain macros.
     */
    public String key;

    /**
     * The key of the user tag pair to tag an upload with.
     * Can contain macros.
     */
    public String value;

    @DataBoundConstructor
    public TagPair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public Descriptor<TagPair> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public final static DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<TagPair> {

        @Override
        public String getDisplayName() {
            return "Tag";
        }
    };
}

