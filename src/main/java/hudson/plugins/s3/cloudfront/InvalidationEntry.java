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
     * Destination bucket for the copy. Can contain macros.
     */
    public String bucket;
    /**
     * Key prefix of S3 files to invalidate.
     * Can contain macros.
     */
    public String keyPrefix;
    
    /**
     * Do not invalidate the artifacts when build fails
     */
    public boolean noInvalidateOnFailure;

    @DataBoundConstructor
    public InvalidationEntry(String bucket, String keyPrefix, boolean noInvalidateOnFailure) {
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
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
        
    	public FormValidation doCheckBucket(@QueryParameter String bucket) {
			return checkNotBlank(bucket, "Bucket name must be speified");
		}
		
		public FormValidation doCheckKeyPrefix(@QueryParameter String keyPrefix) {
			return checkNotBlank(keyPrefix, "Key prefix name must be speified");
		}

		private FormValidation checkNotBlank(String value, String errorMessage) {
			return StringUtils.isNotBlank(value) ? FormValidation.ok() 
					: FormValidation.error(errorMessage);
		}
    };

}
