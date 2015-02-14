package hudson.plugins.s3;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.net.URI;
import java.net.URISyntaxException;

public final class Redirect extends AbstractDescribableImpl<Redirect> {
    public final String bucket;
    public final String key;
    public final String redirectLocation;
    
    public String getKey() {
        return this.key;
    }
    
    public String getRedirectLocation() {
        return this.redirectLocation;
    }
    
    @DataBoundConstructor
    public Redirect(String bucket, String key, String redirectLocation) {
        this.bucket = bucket;
        this.key = key;
        this.redirectLocation = redirectLocation;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Redirect> {
        public String getDisplayName() {
            return "S3 Redirect";
        }
        
        public FormValidation doCheckKey(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.warning("This field cannot be empty.");
            }
            
            if (value.charAt(0) == '/') {
                return FormValidation.error("Key should not have a leading slash `/`.");
            }
            
            return FormValidation.ok();
        }


        /**
         * A valid redirect is a URI that is either:
         * 1. has a HTTP or HTTPS Scheme
         * 2. does not have a scheme and points to a key in the same bucket.
         */
        public FormValidation doCheckRedirectLocation(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.warning("This field cannot be empty.");
            }
            
            try {
                URI uri = new URI(value);

                if ("http".equals(uri.getScheme()) 
                        || "https".equals(uri.getScheme()) 
                        || ((uri.getScheme() == null) && (uri.getPath().charAt(0) == '/'))) {
                    return FormValidation.ok();
                } else {
                    return FormValidation.error(
                            "RedirectLocation must refer to `http`, `https` or to a key in the same bucket (beginning with '/').");
                }
            } catch (URISyntaxException e) {
                return FormValidation.error("Not a valid URI.");
            }
        }
    }
}
