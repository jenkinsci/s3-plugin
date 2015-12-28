package hudson.plugins.s3.cloudfront;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public final class InvalidationEntry implements Describable<InvalidationEntry> {

    /**
     * Origin of the distribution.
     * Can contain macros.
     */
    public String origin;
    /**
     * Invalidation Path.
     * Can contain macros and wildcards.
     */
    public String invalidationPath;
    
    /**
     * Do not invalidate the artifacts when build fails
     */
    public boolean noInvalidateOnFailure;

    @DataBoundConstructor
    public InvalidationEntry(String origin, String invalidationPath, boolean noInvalidateOnFailure) {
        this.origin = origin;
        this.invalidationPath = invalidationPath;
        this.noInvalidateOnFailure = noInvalidateOnFailure;
    }

    public Descriptor<InvalidationEntry> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public final static DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends  Descriptor<InvalidationEntry> {

        @Override
        public String getDisplayName() {
            return "Files to invalidate";
        }
        
        public FormValidation doCheckOrigin(@QueryParameter String origin) {
            return checkNotBlank(origin, "Origin name must be speсified");
        }
        
		public FormValidation doCheckInvalidationPathx(@QueryParameter String invalidationPath) {
			return checkNotBlank(invalidationPath, "Invalidation path must be speсified");
		}

		private FormValidation checkNotBlank(String value, String errorMessage) {
			return StringUtils.isNotBlank(value) ? FormValidation.ok() 
					: FormValidation.error(errorMessage);
		}
    };

}
