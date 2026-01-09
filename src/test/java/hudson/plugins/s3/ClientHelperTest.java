package hudson.plugins.s3;

import hudson.ProxyConfiguration;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ClientHelper}.
 */
public class ClientHelperTest {

    /**
     * Tests that shouldUseProxy returns false when proxy is null.
     */
    @Test
    public void shouldUseProxy_withNullProxy_returnsFalse() throws Exception {
        boolean result = invokeShouldUseProxy(null, URI.create("https://s3.amazonaws.com"));
        assertFalse("Should return false when proxy is null", result);
    }

    /**
     * Tests that shouldUseProxy returns true when endpoint is null (standard AWS region).
     * This is the fix for the NullPointerException regression.
     */
    @Test
    public void shouldUseProxy_withNullEndpoint_returnsTrue() throws Exception {
        ProxyConfiguration proxy = new ProxyConfiguration("proxy.example.com", 8080);
        boolean result = invokeShouldUseProxy(proxy, null);
        assertTrue("Should return true when endpoint is null (standard AWS region)", result);
    }

    /**
     * Tests that shouldUseProxy returns true for a valid endpoint with proxy configured.
     */
    @Test
    public void shouldUseProxy_withValidEndpointAndProxy_returnsTrue() throws Exception {
        ProxyConfiguration proxy = new ProxyConfiguration("proxy.example.com", 8080);
        boolean result = invokeShouldUseProxy(proxy, URI.create("https://s3.eu-central-1.amazonaws.com"));
        assertTrue("Should return true for endpoint not in no-proxy list", result);
    }

    /**
     * Tests that shouldUseProxy returns false when endpoint matches no-proxy pattern.
     */
    @Test
    public void shouldUseProxy_withEndpointMatchingNoProxy_returnsFalse() throws Exception {
        ProxyConfiguration proxy = new ProxyConfiguration(
                "proxy.example.com", 8080, null, null, "*.amazonaws.com");
        boolean result = invokeShouldUseProxy(proxy, URI.create("https://s3.eu-central-1.amazonaws.com"));
        assertFalse("Should return false when endpoint matches no-proxy pattern", result);
    }

    /**
     * Tests that shouldUseProxy handles endpoint with null host gracefully.
     */
    @Test
    public void shouldUseProxy_withEndpointHavingNullHost_returnsTrue() throws Exception {
        ProxyConfiguration proxy = new ProxyConfiguration("proxy.example.com", 8080);
        // URI with just a path, no host
        URI uriWithNoHost = URI.create("/path/to/resource");
        boolean result = invokeShouldUseProxy(proxy, uriWithNoHost);
        assertTrue("Should return true when endpoint host is null", result);
    }

    /**
     * Helper method to invoke the private shouldUseProxy method using reflection.
     */
    private boolean invokeShouldUseProxy(ProxyConfiguration proxy, URI endpoint) throws Exception {
        Method method = ClientHelper.class.getDeclaredMethod("shouldUseProxy", ProxyConfiguration.class, URI.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, proxy, endpoint);
    }
}

