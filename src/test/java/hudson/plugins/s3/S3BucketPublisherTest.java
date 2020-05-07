package hudson.plugins.s3;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.UrlUtils;
import hudson.model.Item;
import hudson.security.SecurityRealm;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class S3BucketPublisherTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfigExists() throws Exception {
        SecurityRealm securityRealm = j.createDummySecurityRealm();
        j.getInstance().setSecurityRealm(securityRealm);
        j.getInstance().setAuthorizationStrategy(
                new MockAuthorizationStrategy().grant(Item.READ, Item.DISCOVER).everywhere().toAuthenticated()
                        .grant(Jenkins.READ, Item.DISCOVER).everywhere().toEveryone()
                        .grant(Item.CONFIGURE).everywhere().to("bob")
                        .grant(Jenkins.ADMINISTER).everywhere().to("alice"));
        j.jenkins.setCrumbIssuer(null);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(
                UrlUtils.toUrlUnsafe(webClient.getContextPath() + "publisher/S3BucketPublisher/loginCheck?name=myname&accessKey=myAccess&secretKey=mykey&useRole=false"),
                HttpMethod.POST);

        webClient.login("bob", "bob");
        Assert.assertEquals(403, webClient.getPage(request).getWebResponse().getStatusCode());

        webClient = j.createWebClient().login("alice", "alice");
        Assert.assertEquals(200, webClient.getPage(request).getWebResponse().getStatusCode());
    }
}
