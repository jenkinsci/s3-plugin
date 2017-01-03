package hudson.plugins.s3;


import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

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
        final Jenkins instance = Jenkins.getInstance();
        final S3Profile.DescriptorImpl s3Plugin = (S3Profile.DescriptorImpl) instance.getDescriptor( S3Profile.class );

        final S3Profile profile = new S3Profile("S3 profile random name", null, null, null, null, true, 0, "0", "0", "0", "0", true, false, false);
        final List<S3Profile> profileList = new ArrayList<>();
        profileList.add(profile);

        s3Plugin.replaceProfiles(profileList);

        HtmlPage page = j.createWebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "S3 profile random name");
    }
}
