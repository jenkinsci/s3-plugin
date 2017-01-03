package hudson.plugins.s3;

import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Fingerprint;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class S3BucketCopyArtifact extends Builder implements SimpleBuildStep {

    private String profileName;
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final List<DownloadEntry> entries;
    private final String target;

    @DataBoundConstructor
    public S3BucketCopyArtifact(String profileName, List<DownloadEntry> entries, String target, boolean flatten, boolean optional) {
        this.target = Util.fixNull(target).trim();

        if (profileName == null) {
            // defaults to the first one
            final S3Profile[] sites = S3Profile.DESCRIPTOR.getProfiles();
            if (sites.length > 0) {
                profileName = sites[0].getName();
            }
        }

        this.profileName = profileName;
        this.entries = entries;

    }

    protected Object readResolve() {
        return this;
    }

    @SuppressWarnings("unused")
    public List<DownloadEntry> getEntries() {
        return entries;
    }

    @SuppressWarnings("unused")
    public String getProfileName() {
        return this.profileName;
    }

    public String getTarget() {
        return target;
    }

    public S3Profile getProfile() {
        return getProfile(profileName);
    }

    public static S3Profile getProfile(String profileName) {
        final S3Profile[] profiles = S3Profile.DESCRIPTOR.getProfiles();

        if (profileName == null && profiles.length > 0)
            // default
            return profiles[0];

        for (S3Profile profile : profiles) {
            if (profile.getName().equals(profileName))
                return profile;
        }
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return ImmutableList.of(new S3ArtifactsProjectAction(project));
    }

    private void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + ' ' + message);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException {
        final PrintStream console = listener.getLogger();
        if (Result.ABORTED.equals(run.getResult())) {
            log(console, "Skipping publishing on S3 because build aborted");
            return;
        }

        if (run.isBuilding()) {
            log(console, "Build is still running");
        }

        final S3Profile profile = getProfile();

        if (profile == null) {
            log(console, "No S3 profile is configured.");
            run.setResult(Result.UNSTABLE);
            return;
        }


        log(console, "Using S3 profile: " + profile.getName());

        try {
            final Map<String, String> envVars = run.getEnvironment(listener);
            final Map<String, String> record = Maps.newHashMap();
            final List<FingerprintRecord> artifacts = Lists.newArrayList();

            for (DownloadEntry downloadEntry : entries) {
                final String expanded = Util.replaceMacro(downloadEntry.includedPattern, envVars);
                final String exclude = Util.replaceMacro(downloadEntry.excludedPattern, envVars);
                if (expanded == null) {
                    throw new IOException();
                }
                FilePath targetDir = ws;
                if (!downloadEntry.target.isEmpty()) {
                    targetDir = new FilePath(targetDir, Util.replaceMacro(target, envVars));
                }


                String bucketName = S3Artifact.getBucket(downloadEntry.bucket, profile.getDefaultBucket());
                final String selRegion = downloadEntry.selectedRegion;

                final List<FingerprintRecord> fingerprints = Lists.newArrayList();

                final S3Objects objectsWithPrefix = profile.getObjectsWithPrefix(bucketName);
                for (S3ObjectSummary objectSummary : objectsWithPrefix) {
                    final S3Artifact artifact = createS3Artifact(objectSummary, selRegion);
                    final Destination dest = Destination.newFromRun(run, artifact, profile.getDefaultBucket());
                    final String artifactName = artifact.getName();
                    final FilePath target = getFilePath(targetDir, downloadEntry.flatten, artifactName);

                    if (FileHelper.selected(expanded, exclude, artifactName)) {
                        fingerprints.add(repeat(profile.getMaxDownloadRetries(), profile.getDownloadRetryTime(), dest, new Callable<FingerprintRecord>() {
                            @Override
                            public FingerprintRecord call() throws IOException, InterruptedException {
                                final String md5 = target.act(new S3DownloadCallable(profile, dest, artifact.getRegion()));
                                return new FingerprintRecord(true, dest.bucketName, target.getName(), artifact.getRegion(), md5);
                            }
                        }));
                    }
                }
            }

            // don't bother adding actions if none of the artifacts are managed
            if (!artifacts.isEmpty()) {
                run.addAction(new S3ArtifactsAction(run, profile, artifacts));
                run.addAction(new FingerprintAction(run, record));
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            run.setResult(Result.UNSTABLE);
        }
    }

    private S3Artifact createS3Artifact(S3ObjectSummary objectSummary, String region) {
        return new S3Artifact(region, objectSummary.getBucketName(), objectSummary.getKey());
    }

    /* start copy from S3Profile - TODO refactor this */
    private FilePath getFilePath(FilePath targetDir, boolean flatten, String fullName) {
        if (flatten) {
            return new FilePath(targetDir, FilenameUtils.getName(fullName));
        }
        else  {
            return new FilePath(targetDir, fullName);
        }
    }

    private <T> T repeat(int maxRetries, int waitTime, Destination dest, Callable<T> func) throws IOException, InterruptedException {
        int retryCount = 0;

        while (true) {
            try {
                return func.call();
            } catch (Exception e) {
                retryCount++;
                if(retryCount >= maxRetries){
                    throw new IOException("Call fails for " + dest + ": " + e + ":: Failed after " + retryCount + " tries.", e);
                }
                Thread.sleep(TimeUnit.SECONDS.toMillis(waitTime));
            }
        }
    }
    /* end copy */

    private void fillFingerprints(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener, Map<String, String> record, List<FingerprintRecord> fingerprints) throws IOException {
        for (FingerprintRecord r : fingerprints) {
            final Fingerprint fp = r.addRecord(run);
            if (fp == null) {
                listener.error("Fingerprinting failed for " + r.getName());
                continue;
            }
            fp.addFor(run);
            record.put(r.getName(), fp.getHashString());
        }
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public DescriptorImpl(Class<? extends Builder> clazz) {
            super(clazz);
            load();
        }

        @SuppressWarnings("unused")
        public List<Region> regions = UploadEntry.regions;

        @SuppressWarnings("unused")
        public String[] storageClasses = UploadEntry.storageClasses;

        public DescriptorImpl() {
            this(S3BucketCopyArtifact.class);
        }

        @Override
        public String getDisplayName() {
            return "S3 Bucket Copy Artifact";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/s3/help.html";
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillProfileNameItems() {
            final ListBoxModel model = new ListBoxModel();
            for (S3Profile profile : S3Profile.DESCRIPTOR.getProfiles()) {
                model.add(profile.getName(), profile.getName());
            }
            return model;
        }


        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
