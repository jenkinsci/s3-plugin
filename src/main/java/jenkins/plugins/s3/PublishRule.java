package jenkins.plugins.s3;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import org.kohsuke.stapler.DataBoundConstructor;

public class PublishRule extends AbstractDescribableImpl<PublishRule> {

    private String workingDir;
    private String include;
    private String exclude;
    private String to;

    public String getInclude() {
        return include;
    }

    public String getExclude() {
        return exclude;
    }

    public String getTo() {
        return to;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    @DataBoundConstructor
    public PublishRule(String workingDir, String include, String exclude, String to) {
        this.workingDir = workingDir;
        this.include = include;
        this.exclude = exclude;
        this.to = to;
    }

    public String toString() {
        return String.format("{workingDir: %s, include: %s, exclude: %s, to: %s}", this.workingDir,
                this.include, this.exclude, this.to);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PublishRule> {
        public String getDisplayName() {
            return "Copy Path";
        }
    }
}