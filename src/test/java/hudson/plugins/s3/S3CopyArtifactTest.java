package hudson.plugins.s3;

import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class S3CopyArtifactTest {

    private static final String PROJECT_NAME = "projectA";
    private static final String FILTER = "filterA";
    private static final String EXCLUDE_FILTER = "excludeFilterA";
    private static final String TARGET = "targetA";
    private static final boolean FLATTEN = true;
    private static final boolean OPTION = true;

    @Test
    void testConfigParser(JenkinsRule j) throws Exception {
        j.createFreeStyleProject(PROJECT_NAME);
        S3CopyArtifact before = new S3CopyArtifact(PROJECT_NAME, null, FILTER, EXCLUDE_FILTER, TARGET, FLATTEN, OPTION);

        S3CopyArtifact after = recreateFromConfig(j, before);

        testGetters(after, PROJECT_NAME, FILTER, EXCLUDE_FILTER, TARGET, FLATTEN, OPTION);
        j.assertEqualBeans(before, after, "projectName,filter,excludeFilter,target,flatten,optional");
    }

    @Test
    void testConfigParserIncorrectProject(JenkinsRule j) throws Exception {
        j.createFreeStyleProject("projectB");
        S3CopyArtifact before = new S3CopyArtifact(PROJECT_NAME, null, FILTER, EXCLUDE_FILTER, TARGET, FLATTEN, OPTION);

        S3CopyArtifact after = recreateFromConfig(j, before);

        testGetters(after, "", FILTER, EXCLUDE_FILTER, TARGET, FLATTEN, OPTION);
        j.assertEqualBeans(before, after, "projectName,filter,excludeFilter,target,flatten,optional");
    }

    private S3CopyArtifact recreateFromConfig(JenkinsRule j, S3CopyArtifact before) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(before);

        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        return p.getBuildersList().get(S3CopyArtifact.class);
    }

    private void testGetters(S3CopyArtifact after, String projectName, String filter, String excludeFilter, String target, boolean flatten, boolean option) {
        assertEquals(projectName, after.getProjectName());
        assertEquals(filter, after.getFilter());
        assertEquals(excludeFilter, after.getExcludeFilter());
        assertEquals(target, after.getTarget());
        assertEquals(flatten, after.isFlatten());
        assertEquals(option, after.isOptional());
    }
}
