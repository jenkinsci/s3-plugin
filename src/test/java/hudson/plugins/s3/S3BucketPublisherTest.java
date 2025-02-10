package hudson.plugins.s3;

import hudson.model.Item;
import hudson.security.SecurityRealm;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.UrlUtils;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class S3BucketPublisherTest {

    @Test
    void testConfigExists(JenkinsRule j) throws Exception {
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
        assertEquals(403, webClient.getPage(request).getWebResponse().getStatusCode());

        webClient = j.createWebClient().login("alice", "alice");
        assertEquals(200, webClient.getPage(request).getWebResponse().getStatusCode());
    }
}
