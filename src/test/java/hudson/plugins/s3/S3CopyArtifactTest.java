package hudson.plugins.s3;
import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class S3CopyArtifactTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private String projectName;
    private String filter;
    private String excludeFilter;
    private String target;
    private boolean flatten;
    private boolean option;

    @Before
    public void setUp() throws Exception {
        projectName = "projectA";
        filter = "filterA";
        excludeFilter = "excludeFilterA";
        target = "targetA";
        flatten = true;
        option = true;
    }

    @Test
    public void testConfigParser() throws Exception {
        j.createFreeStyleProject(projectName);
        S3CopyArtifact before = new S3CopyArtifact(projectName, null, filter, excludeFilter, target, flatten, option);

        S3CopyArtifact after = recreateFromConfig(before);

        testGetters(after, projectName, filter, excludeFilter, target, flatten, option);
        j.assertEqualBeans(before, after, "projectName,filter,excludeFilter,target,flatten,optional");
    }

    @Test
    public void testConfigParserIncorrectProject() throws Exception {
        j.createFreeStyleProject("projectB");
        S3CopyArtifact before = new S3CopyArtifact(projectName, null, filter, excludeFilter, target, flatten, option);

        S3CopyArtifact after = recreateFromConfig(before);

        testGetters(after, "", filter, excludeFilter, target, flatten, option);
        j.assertEqualBeans(before, after, "projectName,filter,excludeFilter,target,flatten,optional");
    }

    private S3CopyArtifact recreateFromConfig(S3CopyArtifact before) throws Exception {
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
