/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.s3;

import com.google.common.collect.Maps;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.copyartifact.*;
import hudson.security.AccessControlled;
import hudson.security.SecurityRealm;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * This is a S3 variant of the CopyArtifact plugin:
 * Build step to copy artifacts from another project.
 * @author Alan Harder
 */
public class S3CopyArtifact extends Builder {

    private String projectName;
    private final String filter, target;
    private /*almost final*/ BuildSelector selector;
    private final Boolean flatten, optional;

    private static final BuildSelector DEFAULT_BUILD_SELECTOR = new StatusBuildSelector(true);

    @DataBoundConstructor
    public S3CopyArtifact(String projectName, BuildSelector buildSelector, String filter, String target,
                        boolean flatten, boolean optional) {
        // Prevents both invalid values and access to artifacts of projects which this user cannot see.
        // If value is parameterized, it will be checked when build runs.
        if (projectName.indexOf('$') < 0 && new JobResolver(projectName).job == null)
            projectName = ""; // Ignore/clear bad value to avoid ugly 500 page
        this.projectName = projectName;

        if (buildSelector == null) {
            buildSelector = DEFAULT_BUILD_SELECTOR;
        }
        this.selector = buildSelector;

        this.filter = Util.fixNull(filter).trim();
        this.target = Util.fixNull(target).trim();
        this.flatten = flatten ? Boolean.TRUE : null;
        this.optional = optional ? Boolean.TRUE : null;
    }

    public String getProjectName() {
        return projectName;
    }

    public BuildSelector getBuildSelector() {
        return selector;
    }

    public String getFilter() {
        return filter;
    }

    public String getTarget() {
        return target;
    }

    public boolean isFlatten() {
        return flatten != null && flatten;
    }

    public boolean isOptional() {
        return optional != null && optional;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
        PrintStream console = listener.getLogger();
        String expandedProject = projectName, expandedFilter = filter;
        try {
            EnvVars env = build.getEnvironment(listener);
            env.overrideAll(build.getBuildVariables()); // Add in matrix axes..
            expandedProject = env.expand(projectName);
            JobResolver job = new JobResolver(expandedProject);
            if (job.job != null && !expandedProject.equals(projectName)
                // If projectName is parameterized, need to do permission check on source project.
                // Would like to check if user who started build has permission, but unable to get
                // Authentication object for arbitrary user.. instead, only allow use of parameters
                // to select jobs which are accessible to all authenticated users.
                && !job.job.getACL().hasPermission(
                        new UsernamePasswordAuthenticationToken("authenticated", "",
                                new GrantedAuthority[]{ SecurityRealm.AUTHENTICATED_AUTHORITY }),
                        Item.READ)) {
                job.job = null; // Disallow access
            }
            if (job.job == null) {
                console.println(Messages.CopyArtifact_MissingProject(expandedProject));
                return false;
            }
            Run src = selector.getBuild(job.job, env, job.filter, build);
            if (src == null) {
                console.println(Messages.CopyArtifact_MissingBuild(expandedProject));
                return isOptional();  // Fail build unless copy is optional
            }
            FilePath targetDir = build.getWorkspace(), baseTargetDir = targetDir;
            if (targetDir == null || !targetDir.exists()) {
                console.println(Messages.CopyArtifact_MissingWorkspace()); // (see JENKINS-3330)
                return isOptional();  // Fail build unless copy is optional
            }
            // Add info about the selected build into the environment
            EnvAction envData = build.getAction(EnvAction.class);
            if (envData != null) {
                envData.add(expandedProject, src.getNumber());
            }
            if (target.length() > 0) targetDir = new FilePath(targetDir, env.expand(target));
            expandedFilter = env.expand(filter);
            if (expandedFilter.trim().length() == 0) expandedFilter = "**";

            if (src instanceof MavenModuleSetBuild) {
                // Copy artifacts from the build (ArchiveArtifacts build step)
                boolean ok = perform(src, build, expandedFilter, targetDir, baseTargetDir, console);
                // Copy artifacts from all modules of this Maven build (automatic archiving)
                for (Run r : ((MavenModuleSetBuild)src).getModuleLastBuilds().values())
                    ok |= perform(r, build, expandedFilter, targetDir, baseTargetDir, console);
                return ok;
            } else if (src instanceof MatrixBuild) {
                boolean ok = false;
                // Copy artifacts from all configurations of this matrix build
                // Use MatrixBuild.getExactRuns if available
                for (Run r : ((MatrixBuild) src).getExactRuns())
                    // Use subdir of targetDir with configuration name (like "jdk=java6u20")
                    ok |= perform(r, build, expandedFilter, targetDir.child(r.getParent().getName()),
                                  baseTargetDir, console);
                return ok;
            } else {
                return perform(src, build, expandedFilter, targetDir, baseTargetDir, console);
            }
        }
        catch (IOException ex) {
            Util.displayIOException(ex, listener);
            ex.printStackTrace(listener.error(
                    Messages.CopyArtifact_FailedToCopy(expandedProject, expandedFilter)));
            return false;
        }
    }

    private boolean perform(Run src, AbstractBuild<?,?> dst, String expandedFilter, FilePath targetDir,
            FilePath baseTargetDir, PrintStream console)
            throws IOException, InterruptedException {

        S3ArtifactsAction action = src.getAction(S3ArtifactsAction.class);
        if (action == null) {
          console.println("Build " + src.getDisplayName() + "[" + src.number + "] doesn't have any S3 artifacts uploaded");
          return false;
        }

        S3Profile profile = S3BucketPublisher.getProfile(action.getProfile());

        if (profile == null) {
            console.println("Can't find S3 profile");
            return false;
        }

        try {
            targetDir.mkdirs();
            List<FingerprintRecord> records = profile.downloadAll(src, action.getArtifacts(), expandedFilter, targetDir, isFlatten(), console);

            Map<String, String> fingerprints = Maps.newHashMap();
            for(FingerprintRecord record : records) {
                FingerprintMap map = Jenkins.getInstance().getFingerprintMap();

                Fingerprint f = map.getOrCreate(src, record.getName(), record.getFingerprint());
                f.add((AbstractBuild)src);
                f.add(dst);
                fingerprints.put(record.getName(), record.getFingerprint());
            }

            for (AbstractBuild r : new AbstractBuild[]{src instanceof AbstractBuild ? (AbstractBuild)src : null,dst}) {
                if (r == null)
                    continue;

                FingerprintAction fa = r.getAction(FingerprintAction.class);
                if (fa != null) fa.add(fingerprints);
                else            r.getActions().add(new FingerprintAction(r, fingerprints));
            }

            console.println(MessageFormat.format("Copied {0} {0,choice,0#artifacts|1#artifact|1<artifacts} from \"{1}\" build number {2} stored in S3", fingerprints.size(), HyperlinkNote.encodeTo('/'+ src.getParent().getUrl(), src.getParent().getFullDisplayName()),
                    HyperlinkNote.encodeTo('/'+src.getUrl(), Integer.toString(src.getNumber()))));
            // Fail build if 0 files copied unless copy is optional
            return fingerprints.size() > 0 || isOptional();
        } finally {
        }
    }

    // Find the job from the given name; usually just a Hudson.getItemByFullName lookup,
    // but this class encapsulates additional logic like filtering on parameters.
    private static class JobResolver {
        Job<?,?> job;
        BuildFilter filter = new BuildFilter();

        JobResolver(String projectName) {
            Hudson hudson = Hudson.getInstance();
            job = hudson.getItemByFullName(projectName, Job.class);
            if (job == null) {
                // Check for parameterized job with filter (see help file)
                int i = projectName.indexOf('/');
                if (i > 0) {
                    Job<?,?> candidate = hudson.getItemByFullName(projectName.substring(0, i), Job.class);
                    if (candidate != null) {
                        ParametersBuildFilter pFilter = new ParametersBuildFilter(projectName.substring(i + 1));
                        if (pFilter.isValid(candidate)) {
                            job = candidate;
                            filter = pFilter;
                        }
                    }
                }
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AccessControlled anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            FormValidation result;
            Item item = new JobResolver(value).job;
            if (item != null) {
                
                result = item instanceof MavenModuleSet
                       ? FormValidation.warning(Messages.CopyArtifact_MavenProject())
                       : (item instanceof MatrixProject
                          ? FormValidation.warning(Messages.CopyArtifact_MatrixProject())
                          : FormValidation.ok());
            }
            else if (value.indexOf('$') >= 0)
                result = FormValidation.warning(Messages.CopyArtifact_ParameterizedName());
            else
                result = FormValidation.error(
                    hudson.tasks.Messages.BuildTrigger_NoSuchProject(
                        value, AbstractProject.findNearest(value).getName()));
            return result;
        }

        public boolean isApplicable(Class<? extends AbstractProject> clazz) {
            return true;
        }

        public String getDisplayName() {
            return "S3 Copy Artifact";
        }

        public DescriptorExtensionList<BuildSelector,Descriptor<BuildSelector>> getBuildSelectors() {
            final DescriptorExtensionList<BuildSelector, Descriptor<BuildSelector>> list = DescriptorExtensionList.createDescriptorList(Jenkins.getInstance(), BuildSelector.class);
            // remove from list some of the CopyArchiver build selector that we can't deal with
            list.remove(WorkspaceSelector.DESCRIPTOR);
            return list;
        }
    }

    // Listen for project renames and update property here if needed.
    @Extension
    public static final class ListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            for (AbstractProject<?,?> project
                    : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                for (S3CopyArtifact ca : getCopiers(project)) try {
                    if (ca.getProjectName().equals(oldName))
                        ca.projectName = newName;
                    else if (ca.getProjectName().startsWith(oldName + '/'))
                        // Support rename for "MatrixJobName/AxisName=value" type of name
                        ca.projectName = newName + ca.projectName.substring(oldName.length());
                    else continue;
                    project.save();
                } catch (IOException ex) {
                    Logger.getLogger(ListenerImpl.class.getName()).log(Level.WARNING,
                            "Failed to resave project " + project.getName()
                            + " for project rename in S3 Copy Artifact build step ("
                            + oldName + " =>" + newName + ")", ex);
                }
            }
        }

        private static List<S3CopyArtifact> getCopiers(AbstractProject project) {
            DescribableList<Builder,Descriptor<Builder>> list =
                    project instanceof Project ? ((Project<?,?>)project).getBuildersList()
                      : (project instanceof MatrixProject ?
                          ((MatrixProject)project).getBuildersList() : null);
            if (list == null) return Collections.emptyList();
            return (List<S3CopyArtifact>)list.getAll(S3CopyArtifact.class);
        }
    }

    // Listen for new builds and add EnvAction in any that use CopyArtifact build step
    @Extension
    public static final class CopyArtifactRunListener extends RunListener<Build> {
        public CopyArtifactRunListener() {
            super(Build.class);
        }

        @Override
        public void onStarted(Build r, TaskListener listener) {
            if (((Build<?,?>)r).getProject().getBuildersList().get(S3CopyArtifact.class) != null)
                r.addAction(new EnvAction());
        }
    }
    
    private static class EnvAction implements EnvironmentContributingAction {
        // Decided not to record this data in build.xml, so marked transient:
        private transient Map<String,String> data = new HashMap<String,String>();

        private void add(String projectName, int buildNumber) {
            if (data==null) return;
            int i = projectName.indexOf('/'); // Omit any detail after a /
            if (i > 0) projectName = projectName.substring(0, i);
            data.put("COPYARTIFACT_BUILD_NUMBER_"
                       + projectName.toUpperCase().replaceAll("[^A-Z]+", "_"), // Only use letters and _
                     Integer.toString(buildNumber));
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            if (data!=null) env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
    }
}
