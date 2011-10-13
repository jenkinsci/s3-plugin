package hudson.plugins.s3;


import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

public class S3Test extends HudsonTestCase {

    public void testConfig() throws Exception {
        HtmlPage page = new WebClient().goTo("configure");
        WebAssert.assertTextPresent(page, "S3 profiles");
    }
}
