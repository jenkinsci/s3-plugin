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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class S3BucketPublisher extends Recorder implements Describable<Publisher> {
    
    private static final Logger log = Logger.getLogger(S3BucketPublisher.class.getName());
    
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
    public boolean perform(AbstractBuild<?, ?> build,
                           Launcher launcher,
                           BuildListener listener)
            throws InterruptedException, IOException {

        final boolean buildFailed = build.getResult() == Result.FAILURE;
        
        S3Profile profile = getProfile();
        if (profile == null) {
            log(listener.getLogger(), "No S3 profile is configured.");
            build.setResult(Result.UNSTABLE);
            return true;
        }
        log(listener.getLogger(), "Using S3 profile: " + profile.getName());
        try {
            Map<String, String> envVars = build.getEnvironment(listener);
            Map<String,String> record = Maps.newHashMap();
            List<FingerprintRecord> artifacts = Lists.newArrayList();
            
            for (Entry entry : entries) {
                
                if (entry.noUploadOnFailure && buildFailed) {
                    // build failed. don't post
                    log(listener.getLogger(), "Skipping publishing on S3 because build failed");
                    continue;
                }
                
                String expanded = Util.replaceMacro(entry.sourceFile, envVars);
                FilePath ws = build.getWorkspace();
                FilePath[] paths = ws.list(expanded);

                if (paths.length == 0) {
                    // try to do error diagnostics
                    log(listener.getLogger(), "No file(s) found: " + expanded);
                    String error = ws.validateAntFileMask(expanded);
                    if (error != null)
                        log(listener.getLogger(), error);
                }

                int searchPathLength = getSearchPathLength(ws.getRemote(), expanded);

                String bucket = Util.replaceMacro(entry.bucket, envVars);
                String storageClass = Util.replaceMacro(entry.storageClass, envVars);
                String selRegion = entry.selectedRegion;
                List<MetadataPair> escapedUserMetadata = new ArrayList<MetadataPair>();
                for (MetadataPair metadataPair : userMetadata) {
                    escapedUserMetadata.add(
                        new MetadataPair(
                            Util.replaceMacro(metadataPair.key, envVars),
                            Util.replaceMacro(metadataPair.value, envVars))
                    );
                }
                
                List<FingerprintRecord> records = Lists.newArrayList();
                
                for (FilePath src : paths) {
                    log(listener.getLogger(), "bucket=" + bucket + ", file=" + src.getName() + " region=" + selRegion + ", upload from slave=" + entry.uploadFromSlave + " managed="+ entry.artifactManagement + " , server encryption "+entry.useServerSideEncryption);
                    records.add(profile.upload(build, listener, bucket, src, searchPathLength, escapedUserMetadata, storageClass, selRegion, entry.uploadFromSlave, entry.artifactManagement, entry.useServerSideEncryption));
                }
                if (entry.isManaged()) {
                    artifacts.addAll(records);
    
                    for (FingerprintRecord r : records) {
                      Fingerprint fp = r.addRecord(build);
                      if(fp==null) {
                          listener.error("Fingerprinting failed for "+r.getName());
                          continue;
                      }
                      fp.add(build);
                      record.put(r.getName(),fp.getHashString());
                   }
                }
            }
            // don't bother adding actions if none of the artifacts are managed
            if (artifacts.size() > 0) {
                build.getActions().add(new S3ArtifactsAction(build, profile, artifacts ));
                build.getActions().add(new FingerprintAction(build,record));
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        }
        return true;
    }

    private int getSearchPathLength(String workSpace, String filterExpanded) {
        File file1 = new File(workSpace);
        File file2 = new File(file1, filterExpanded);

        String pathWithFilter = file2.getPath();

        int indexOfWildCard = pathWithFilter.indexOf("*");

        if (indexOfWildCard > 0)
        {
            String s = pathWithFilter.substring(0, indexOfWildCard);
            return s.length();
        }
        else
        {
            return file2.getParent().length() + 1;
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
            profiles.replaceBy(req.bindParametersToList(S3Profile.class, "s3."));
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
            return profiles.toArray(new S3Profile[0]);
        }

        public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String name = Util.fixEmpty(req.getParameter("name"));
            if (name == null) {// name is not entered yet
                return FormValidation.ok();
            }
            S3Profile profile = new S3Profile(name, req.getParameter("accessKey"), req.getParameter("secretKey"), req.getParameter("proxyHost"), req.getParameter("proxyPort"), false, req.getParameter("maxUploadRetries"), req.getParameter("retryWaitTime"));

            try {
                profile.check();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                return FormValidation.error("Can't connect to S3 service: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
