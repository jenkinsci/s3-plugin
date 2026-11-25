package hudson.plugins.s3;

import hudson.ProxyConfiguration;
import org.junit.Test;

import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
     * The pool overrides are opt-in: with neither property set the SDK defaults must be left
     * alone, so behaviour is unchanged for everyone who does not configure them.
     */
    @Test
    public void connectionPoolOverrides_areNotAppliedWhenUnset() throws Exception {
        System.clearProperty(ClientHelper.MAX_CONCURRENCY_PROPERTY);
        System.clearProperty(ClientHelper.CONNECTION_ACQUISITION_TIMEOUT_PROPERTY);

        NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
        invokeApplyConnectionPoolOverrides(builder);

        verify(builder, never()).maxConcurrency(anyInt());
        verify(builder, never()).connectionAcquisitionTimeout(any(Duration.class));
    }

    @Test
    public void connectionPoolOverrides_areAppliedWhenSet() throws Exception {
        System.setProperty(ClientHelper.MAX_CONCURRENCY_PROPERTY, "200");
        System.setProperty(ClientHelper.CONNECTION_ACQUISITION_TIMEOUT_PROPERTY, "120");
        try {
            NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
            invokeApplyConnectionPoolOverrides(builder);

            verify(builder).maxConcurrency(200);
            verify(builder).connectionAcquisitionTimeout(Duration.ofSeconds(120));
        } finally {
            System.clearProperty(ClientHelper.MAX_CONCURRENCY_PROPERTY);
            System.clearProperty(ClientHelper.CONNECTION_ACQUISITION_TIMEOUT_PROPERTY);
        }
    }

    /**
     * Non-positive values are ignored rather than passed through, since the SDK rejects them and
     * a typo in a system property should not stop uploads working altogether.
     */
    @Test
    public void connectionPoolOverrides_ignoreNonPositiveValues() throws Exception {
        System.setProperty(ClientHelper.MAX_CONCURRENCY_PROPERTY, "0");
        System.setProperty(ClientHelper.CONNECTION_ACQUISITION_TIMEOUT_PROPERTY, "-5");
        try {
            NettyNioAsyncHttpClient.Builder builder = mock(NettyNioAsyncHttpClient.Builder.class);
            invokeApplyConnectionPoolOverrides(builder);

            verify(builder, never()).maxConcurrency(anyInt());
            verify(builder, never()).connectionAcquisitionTimeout(any(Duration.class));
        } finally {
            System.clearProperty(ClientHelper.MAX_CONCURRENCY_PROPERTY);
            System.clearProperty(ClientHelper.CONNECTION_ACQUISITION_TIMEOUT_PROPERTY);
        }
    }

    private void invokeApplyConnectionPoolOverrides(NettyNioAsyncHttpClient.Builder builder) throws Exception {
        Method method = ClientHelper.class.getDeclaredMethod(
                "applyConnectionPoolOverrides", NettyNioAsyncHttpClient.Builder.class);
        method.setAccessible(true);
        method.invoke(null, builder);
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

