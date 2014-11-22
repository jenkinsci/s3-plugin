package hudson.plugins.s3;


import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class S3Test {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testConfig() throws Exception {
        HtmlPage page = jenkins.createWebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "S3 profiles");
    }
}
