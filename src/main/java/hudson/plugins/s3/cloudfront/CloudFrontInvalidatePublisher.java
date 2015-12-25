package hudson.plugins.s3.cloudfront;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.s3.S3BucketPublisher;
import hudson.plugins.s3.S3Profile;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public final class CloudFrontInvalidatePublisher extends Recorder implements Describable<Publisher> {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private String profileName;
    private final List<InvalidationEntry> invalidationEntries;

    public CloudFrontInvalidatePublisher() {
        super();
        this.invalidationEntries = Collections.emptyList();
    }

    @DataBoundConstructor
    public CloudFrontInvalidatePublisher(String profileName,
            List<InvalidationEntry> invalidationEntries) {
        if (StringUtils.isBlank(profileName)) {
            // defaults to the first one
            S3Profile[] sites = DESCRIPTOR.getProfiles();
            if (sites.length > 0)
                profileName = sites[0].getName();
        }

        this.profileName = profileName;
        this.invalidationEntries = invalidationEntries;

    }

    public String getProfileName() {
        return profileName;
    }

    public List<InvalidationEntry> getInvalidationEntries() {
        return invalidationEntries;
    }

    public S3Profile getProfile(String profileName) {
        S3Profile[] profiles = DESCRIPTOR.getProfiles();

        if (profiles.length == 0) {
            return null;
        }
        for (S3Profile profile : profiles) {
            if (profile.getName().equals(profileName)) {
                return profile;
            }
        }
        return profiles[0];
    }

    protected void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + " " + message);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        final boolean buildFailed = build.getResult() == Result.FAILURE;

        S3Profile profile = getProfile(profileName);
        if (profile == null) {
            log(listener.getLogger(), "No S3 profile is configured.");
            build.setResult(Result.UNSTABLE);
            return true;
        }
        log(listener.getLogger(), "Using S3 profile: " + profile.getName());
        try {
            Map<String, String> envVars = build.getEnvironment(listener);
            for (InvalidationEntry entry : invalidationEntries) {

                if (entry.noInvalidateOnFailure && buildFailed) {
                    // build failed. don't post
                    log(listener.getLogger(), "Skipping S3 key invalidation because build failed");
                    continue;
                }

                String origin = Util.replaceMacro(entry.origin, envVars);
                if (StringUtils.isBlank(origin)) {
                    log(listener.getLogger(), "Origin was not provided.");
                    continue;
                }
                
                String keyPath = Util.replaceMacro(entry.invalidationPath, envVars);
                if (StringUtils.isBlank(keyPath)) {
                    log(listener.getLogger(), "No S3 asset key was provided.");
                    continue;
                }

                InvalidationRecord invalidationRecord = profile.invalidate(build, listener, origin, keyPath);
                log(listener.getLogger(), "With keyPath = " + keyPath
                        + ";\n\t" + invalidationRecord);
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        }
        return true;
    }

    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl(Class<? extends Publisher> clazz) {
            super(clazz);
            load();
        }

        public DescriptorImpl() {
            this(CloudFrontInvalidatePublisher.class);
        }

        @Override
        public String getDisplayName() {
            return "Invalidate S3 assets among CloudFront distributions";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/s3/help-invalidate.html";
        }

        public ListBoxModel doFillProfileNameItems() {
            ListBoxModel model = new ListBoxModel();
            for (S3Profile profile : getProfiles()) {
                model.add(profile.getName(), profile.getName());
            }
            return model;
        }

        public S3Profile[] getProfiles() {
            S3BucketPublisher.DescriptorImpl s3PublisherDescriptor = (S3BucketPublisher.DescriptorImpl) Jenkins
                    .getInstance().getDescriptor(S3BucketPublisher.class);
            return s3PublisherDescriptor.getProfiles();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
