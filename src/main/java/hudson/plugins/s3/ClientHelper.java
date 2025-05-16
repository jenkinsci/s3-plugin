package hudson.plugins.s3;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;
import io.netty.handler.ssl.SslProvider;
import jenkins.model.Jenkins;
import jenkins.util.JenkinsJVM;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class ClientHelper {
    public final static String DEFAULT_AMAZON_S3_REGION_NAME = System.getProperty(
            "hudson.plugins.s3.DEFAULT_AMAZON_S3_REGION", Region.US_EAST_1.id());
    public static final String ENDPOINT = System.getProperty("hudson.plugins.s3.ENDPOINT", System.getenv("PLUGIN_S3_ENDPOINT"));
    public static final URI ENDPOINT_URI;

    static {
        try {
            ENDPOINT_URI = isNotEmpty(ENDPOINT) ? new URI(ENDPOINT) : null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static S3AsyncClient createAsyncClient(String accessKey, String secretKey, boolean useRole, String region, @CheckForNull ProxyConfiguration proxy, @CheckForNull URI customEndpoint, Long thresholdInBytes) {
        Region awsRegion = getRegionFromString(region);
        S3AsyncClientBuilder builder = S3AsyncClient.builder();//.overrideConfiguration(clientConfiguration);
        builder.region(awsRegion);

        if (!useRole) {
            builder = builder.credentialsProvider(() -> AwsBasicCredentials.create(accessKey, secretKey));
        }

        if (customEndpoint != null) {
            builder = builder.endpointOverride(customEndpoint).forcePathStyle(true);
            builder.httpClient(getAsyncHttpClient(customEndpoint, proxy));
        } else if (ENDPOINT_URI != null) {
            builder = builder.endpointOverride(ENDPOINT_URI).forcePathStyle(true);
            builder.httpClient(getAsyncHttpClient(ENDPOINT_URI, proxy));
        } else {
            builder.httpClient(getAsyncHttpClient(null, proxy));
        }
        if (thresholdInBytes != null) {
            builder.multipartConfiguration(mcb -> mcb.thresholdInBytes(thresholdInBytes));
        }
        return builder.build();
    }

    public static S3Client createClient(String accessKey, String secretKey, boolean useRole, String region, ProxyConfiguration proxy) {
        return createClient(accessKey, secretKey, useRole, region, proxy, ENDPOINT_URI);
    }

    public static S3Client createClient(String accessKey, String secretKey, boolean useRole, String region, ProxyConfiguration proxy, @CheckForNull URI customEndpoint) {
        Region awsRegion = getRegionFromString(region);
        S3ClientBuilder builder = S3Client.builder();
        builder.region(awsRegion);

        if (!useRole) {
            builder = builder.credentialsProvider(new AwsCredentialsProvider() {
                @Override
                public AwsCredentials resolveCredentials() {
                    return AwsBasicCredentials.create(accessKey, secretKey);
                }
            });
        }
        //SdkHttpClient.Builder httpClient = SdkHttpClient.

        try {
            if (customEndpoint != null) {
                builder = builder.endpointOverride(customEndpoint).forcePathStyle(true);
                builder.httpClient(getHttpClient(customEndpoint, proxy));
            } else if (ENDPOINT_URI != null) {
                builder = builder.endpointOverride(ENDPOINT_URI).forcePathStyle(true);
                builder.httpClient(getHttpClient(ENDPOINT_URI, proxy));
            } else {
                builder.httpClient(getHttpClient(null, proxy));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Can't create proxy URI", e);
        }

        return builder.build();
    }

    /**
     * Gets the {@link Region} from its name with backward compatibility concerns and defaulting
     *
     * @param regionName nullable region name
     * @return AWS region, never {@code null}, defaults to {@link Region#US_EAST_1} see {@link #DEFAULT_AMAZON_S3_REGION_NAME}.
     */
    @NonNull
    private static Region getRegionFromString(@CheckForNull String regionName) {
        Region region = null;

        if (regionName == null || regionName.isEmpty()) {
            region = Region.of(DEFAULT_AMAZON_S3_REGION_NAME);
        } else {
            region = Region.of(regionName);
        }
        if (region == null) {
            throw new IllegalStateException("No AWS Region found for name '" + regionName + "' and default region '" + DEFAULT_AMAZON_S3_REGION_NAME + "'");
        }
        return region;
    }

    private static SdkHttpClient getHttpClient(URI serviceEndpoint, ProxyConfiguration proxy) throws URISyntaxException {
        ApacheHttpClient.Builder httpClient1 = ApacheHttpClient.builder();
        if (proxy == null && JenkinsJVM.isJenkinsJVM()) {
            proxy = Jenkins.get().getProxy();
        }
        if (shouldUseProxy(proxy, serviceEndpoint)) {
            software.amazon.awssdk.http.apache.ProxyConfiguration.Builder proxyBuilder = software.amazon.awssdk.http.apache.ProxyConfiguration.builder()
                    .endpoint(new URI("http", null, proxy.getName(), proxy.getPort(), null, null, null));
            if (isNotEmpty(proxy.getUserName())) {
                proxyBuilder
                        .username(proxy.getUserName())
                        .password(proxy.getPassword());
            }
            httpClient1.proxyConfiguration(proxyBuilder.build());
        }
        return httpClient1.build();
    }

    private static SdkAsyncHttpClient getAsyncHttpClient(URI serviceEndpoint, ProxyConfiguration proxy) {
        NettyNioAsyncHttpClient.Builder builder = NettyNioAsyncHttpClient.builder().sslProvider(SslProvider.JDK); //make sure we use BouncyCastle when available
        if (proxy == null && JenkinsJVM.isJenkinsJVM()) {
            proxy = Jenkins.get().getProxy();
        }
        if (shouldUseProxy(proxy, serviceEndpoint)) {
            software.amazon.awssdk.http.nio.netty.ProxyConfiguration.Builder proxyBuilder = software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
                    .host(proxy.getName()).port(proxy.getPort());
            if (isNotEmpty(proxy.getUserName())) {
                proxyBuilder
                        .username(proxy.getUserName())
                        .password(proxy.getPassword());
            }
            builder.proxyConfiguration(proxyBuilder.build());
        }
        return builder.build();
    }

    private static boolean shouldUseProxy(ProxyConfiguration proxy, URI endpoint) {
        if (proxy == null) {
            return false;
        }
        String hostname = endpoint.getHost();
        boolean shouldProxy = true;
        for (Pattern p : proxy.getNoProxyHostPatterns()) {
            if (p.matcher(hostname).matches()) {
                shouldProxy = false;
                break;
            }
        }

        return shouldProxy;
    }
}
