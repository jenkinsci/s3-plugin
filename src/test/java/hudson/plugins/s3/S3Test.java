package hudson.plugins.s3;


import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.s3.S3BucketPublisher.DescriptorImpl;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.toArray;
import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;

public class S3Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfigExists() throws Exception {
        HtmlPage page = j.createWebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "S3 profiles");
    }

    @Test
    public void testConfigContainsProfiles() throws Exception {
        final S3Profile profile = new S3Profile("S3 profile random name", null, null, true, 0, "0", "0", "0", "0", true);

        replaceS3PluginProfile(profile);

        HtmlPage page = j.createWebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "S3 profile random name");
    }

    @Test
    public void multiplePublishersUseExistingActions() throws Exception {
        String profileName = "test profile";
        String fileName = "testFile";
        S3BucketPublisher publisher = new S3BucketPublisher(
                profileName,
                newArrayList(entryForFile(fileName)),
                Collections.<MetadataPair>emptyList(),
                true,
                "INFO",
                "SUCCESS",
                false
        );
        replaceS3PluginProfile(mockS3Profile(profileName));

        final FreeStyleProject project = j.createFreeStyleProject("testing");
        project.getBuildersList().add(stepCreatingFile(fileName));

        project.getPublishersList().add(publisher);
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = j.buildAndAssertSuccess(project);
        assertEquals(1, countActionsOfType(build, S3ArtifactsAction.class));
        assertEquals(1, countActionsOfType(build, FingerprintAction.class));
    }

    @Test
    public void dontSetBuildResultTest() throws Exception {
        String profileName = "test profile";
        String missingProfileName = "test profile missing";
        String fileName = "testFile";
        S3BucketPublisher missingPublisher = new S3BucketPublisher(
                missingProfileName,
                newArrayList(entryForFile(fileName)),
                Collections.<MetadataPair>emptyList(),
                true,
                "DEBUG",
                "SUCCESS",
                true
        );
        replaceS3PluginProfile(mockS3Profile(profileName));

        final FreeStyleProject project = j.createFreeStyleProject("testing");
        project.getBuildersList().add(stepCreatingFile(fileName));

        project.getPublishersList().add(missingPublisher);

        QueueTaskFuture<FreeStyleBuild> r = project.scheduleBuild2(0);
        j.assertBuildStatus(Result.FAILURE, r);
    }

    private Entry entryForFile(String fileName) {
        return new Entry("bucket", fileName, "", "", "", false, false, true, false, false, false, false, false, null);
    }

    private Builder stepCreatingFile(String fileName) {
        if (Functions.isWindows()) {
            return new BatchFile("echo.> " + fileName);
        } else {
            return new Shell("touch " + fileName);
        }
    }

    private void replaceS3PluginProfile(S3Profile s3Profile) {
        final Jenkins instance = Jenkins.getInstance();
        final DescriptorImpl s3Plugin = (DescriptorImpl) instance.getDescriptor(S3BucketPublisher.class);
        s3Plugin.replaceProfiles(newArrayList(s3Profile));
    }

    private S3Profile mockS3Profile(String profileName) throws IOException, InterruptedException {
        S3Profile profile = Mockito.mock(S3Profile.class);
        Mockito.when(profile.getName()).thenReturn(profileName);
        Mockito.when(profile.isKeepStructure()).thenReturn(true);
        Mockito.when(profile.upload(
                Mockito.any(Run.class),
                Mockito.anyString(),
                Mockito.anyList(),
                Mockito.anyList(),
                Mockito.anyMap(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean()
        )).thenReturn(newArrayList(new FingerprintRecord(true, "bucket", "path", "eu-west-1", "xxxx")));
        return profile;
    }

    private int countActionsOfType(Run<?, ?> run, Class<?> actionClass) {
        return getAllActionsOfType(run, actionClass).length;
    }

    private <T> T[] getAllActionsOfType(Run<?, ?> run, Class<T> actionClass) {
        List<? extends Action> actions = run.getAllActions();
        return toArray(filter(actions, actionClass), actionClass);
    }
}
