package hudson.plugins.s3;

import com.amazonaws.regions.Regions;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class S3BucketPublisher extends Recorder implements Describable<Publisher>, SimpleBuildStep {

    private String profileName;
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final List<Entry> entries;

    private boolean dontWaitForConcurrentBuildCompletion;

    /**
     * User metadata key/value pairs to tag the upload with.
     */
    private /*almost final*/ List<MetadataPair> userMetadata;

    @DataBoundConstructor
    public S3BucketPublisher(String profileName, List<Entry> entries, List<MetadataPair> userMetadata,
                             boolean dontWaitForConcurrentBuildCompletion) {
        if (profileName == null) {
            // defaults to the first one
            S3Profile[] sites = DESCRIPTOR.getProfiles();
            if (sites.length > 0)
                profileName = sites[0].getName();
        }

        this.profileName = profileName;
        this.entries = entries;

        if (userMetadata==null)
            userMetadata = new ArrayList<MetadataPair>();
        this.userMetadata = userMetadata;

        this.dontWaitForConcurrentBuildCompletion = dontWaitForConcurrentBuildCompletion;
    }

    protected Object readResolve() {
        if (userMetadata==null)
            userMetadata = new ArrayList<MetadataPair>();
        return this;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public List<MetadataPair> getUserMetadata() {
        return userMetadata;
    }

    public String getProfileName() {
        return this.profileName;
    }

    public boolean isDontWaitForConcurrentBuildCompletion() {
        return dontWaitForConcurrentBuildCompletion;
    }

    public S3Profile getProfile() {
        return getProfile(profileName);
    }

    public static S3Profile getProfile(String profileName) {        
        S3Profile[] profiles = DESCRIPTOR.getProfiles();

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
       
    protected void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + " " + message);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException, IOException {

        final boolean buildFailed = run.getResult() == Result.FAILURE;
        
        S3Profile profile = getProfile();
        if (profile == null) {
            log(listener.getLogger(), "No S3 profile is configured.");

            run.setResult(Result.UNSTABLE);
            return;
        }

        log(listener.getLogger(), "Using S3 profile: " + profile.getName());
        try {
            Map<String, String> envVars = run.getEnvironment(listener);
            Map<String,String> record = Maps.newHashMap();
            List<FingerprintRecord> artifacts = Lists.newArrayList();
            
            for (Entry entry : entries) {
                
                if (entry.noUploadOnFailure && buildFailed) {
                    // build failed. don't post
                    log(listener.getLogger(), "Skipping publishing on S3 because build failed");
                    continue;
                }
                
                String expanded = Util.replaceMacro(entry.sourceFile, envVars);
                String exclude = Util.replaceMacro(entry.excludedFile, envVars);
                FilePath[] paths = ws.list(expanded, exclude);

                if (paths.length == 0) {
                    // try to do error diagnostics
                    log(listener.getLogger(), "No file(s) found: " + expanded);
                    String error = ws.validateAntFileMask(expanded);
                    if (error != null)
                        log(listener.getLogger(), error);
                }

                String bucket = Util.replaceMacro(entry.bucket, envVars);
                String storageClass = Util.replaceMacro(entry.storageClass, envVars);
                String selRegion = entry.selectedRegion;

                Map<String, String> mergedMetadata = new HashMap<String, String>();

                if (userMetadata != null) {
                    for (MetadataPair pair : userMetadata) {
                        mergedMetadata.put(pair.key, pair.value);
                    }
                }

                if (entry.userMetadata != null) {
                    for (MetadataPair pair : entry.userMetadata) {
                        mergedMetadata.put(pair.key, pair.value);
                    }
                }

                Map<String, String> escapedMetadata = new HashMap<String, String>();

                for (Map.Entry<String, String> mapEntry : mergedMetadata.entrySet()) {
                    escapedMetadata.put(
                            Util.replaceMacro(mapEntry.getKey(), envVars),
                            Util.replaceMacro(mapEntry.getValue(), envVars));
                }

                List<FingerprintRecord> records = Lists.newArrayList();

                int workspacePath = ws.getRemote().length() + 1;
                for (FilePath src : paths) {
                    log(listener.getLogger(), "bucket=" + bucket + ", file=" + src.getName() + " region=" + selRegion + ", upload from slave=" + entry.uploadFromSlave + " managed="+ entry.managedArtifacts + " , server encryption "+entry.useServerSideEncryption);
                    records.add(profile.upload(run, listener, bucket, src, workspacePath, escapedMetadata, storageClass, selRegion, entry.uploadFromSlave, entry.managedArtifacts, entry.useServerSideEncryption, entry.flatten, entry.gzipFiles));
                }
                if (entry.managedArtifacts) {
                    artifacts.addAll(records);
    
                    for (FingerprintRecord r : records) {
                      Fingerprint fp = r.addRecord(run);
                      if(fp==null) {
                          listener.error("Fingerprinting failed for "+r.getName());
                          continue;
                      }
                      fp.addFor(run);
                      record.put(r.getName(),fp.getHashString());
                   }
                }
            }
            // don't bother adding actions if none of the artifacts are managed
            if (artifacts.size() > 0) {
                run.getActions().add(new S3ArtifactsAction(run, profile, artifacts ));
                run.getActions().add(new FingerprintAction(run,record));
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            run.setResult(Result.UNSTABLE);
        }
    }

    // Listen for project renames and update property here if needed.
    @Extension
    public static final class S3DeletedJobListener extends RunListener<Run> {
        @Override
        public void onDeleted(Run run) {
            S3ArtifactsAction artifacts = run.getAction(S3ArtifactsAction.class);
            if (artifacts != null) {
                S3Profile profile = S3BucketPublisher.getProfile(artifacts.getProfile());
                for (FingerprintRecord record : artifacts.getArtifacts()) {
                    profile.delete(run, record);
                }
            }
        }
    }
   
    public BuildStepMonitor getRequiredMonitorService() {
        return dontWaitForConcurrentBuildCompletion ? BuildStepMonitor.NONE : BuildStepMonitor.STEP;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private final CopyOnWriteList<S3Profile> profiles = new CopyOnWriteList<S3Profile>();
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public DescriptorImpl(Class<? extends Publisher> clazz) {
            super(clazz);
            load();
        }

        public Regions[] regions = Entry.regions;

        public String[] storageClasses = Entry.storageClasses;

        public DescriptorImpl() {
            this(S3BucketPublisher.class);
        }

        @Override
        public String getDisplayName() {
            return "Publish artifacts to S3 Bucket";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/s3/help.html";
        }

        @Override
        public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
            JSONArray array = json.optJSONArray("profile");
            if (array != null) {
                profiles.replaceBy(req.bindJSONToList(S3Profile.class, array));
            }
            else {
                profiles.replaceBy(req.bindJSON(S3Profile.class, json.getJSONObject("profile")));
            }
            save();
            return true;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillProfileNameItems() {
            ListBoxModel model = new ListBoxModel();
            for (S3Profile profile : profiles) {
                model.add(profile.getName(), profile.getName());
            }
            return model;
        }

        public S3Profile[] getProfiles() {
            S3Profile[] profileArray = new S3Profile[profiles.size()];
            return profiles.toArray(profileArray);
        }

        public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String name = Util.fixNull(req.getParameter("name"));
            String accessKey = Util.fixNull(req.getParameter("accessKey"));
            String secretKey = Util.fixNull(req.getParameter("secretKey"));
            String useIAMCredential = Util.fixNull(req.getParameter("useRole"));

            boolean couldBeValidated = !name.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty();
            boolean useRole = Boolean.parseBoolean(useIAMCredential);

            if (!couldBeValidated) {
                if (name.isEmpty())
                    return FormValidation.ok("Please, enter name");

                if (useRole)
                    return FormValidation.ok();

                if (accessKey.isEmpty())
                    return FormValidation.ok("Please, enter accessKey");

                if (secretKey.isEmpty())
                    return FormValidation.ok("Please, enter secretKey");
            }

            S3Profile profile = new S3Profile(name, accessKey, secretKey, req.getParameter("proxyHost"), req.getParameter("proxyPort"), useRole, req.getParameter("maxUploadRetries"), req.getParameter("retryWaitTime"));

            try {
                profile.check();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                return FormValidation.error("Can't connect to S3 service: " + e.getMessage());
            }
            return FormValidation.ok("Check passed!");
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
