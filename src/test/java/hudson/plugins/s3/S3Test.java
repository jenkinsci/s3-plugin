package hudson.plugins.s3;


import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class S3Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfig() throws Exception {
        HtmlPage page = j.createWebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "S3 profiles");
    }
}
