package hudson.plugins.s3;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import hudson.ProxyConfiguration;

public class ClientHelper {
    public final static String DEFAULT_AMAZON_S3_REGION_NAME = System.getProperty(
            "hudson.plugins.s3.DEFAULT_AMAZON_S3_REGION",
            com.amazonaws.services.s3.model.Region.US_Standard.toAWSRegion().getName());

    public static AmazonS3 createClient(String accessKey, String secretKey, boolean useRole, String region, ProxyConfiguration proxy)
    {
        Region awsRegion = getRegionFromString(region);

        ClientConfiguration clientConfiguration = getClientConfiguration(proxy, awsRegion);

        final AmazonS3 client;
        if (useRole) {
            client = AmazonS3ClientBuilder.standard()
            		.withClientConfiguration(clientConfiguration)
            		.withRegion(region).build();
        } else {
            client = AmazonS3ClientBuilder.standard()
            		.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
            		.withClientConfiguration(clientConfiguration)
            		.withRegion(region).build();
        }

        return client;
    }

    /**
     * Gets the {@link Region} from its name with backward compatibility concerns and defaulting
     *
     * @param regionName nullable region name
     * @return AWS region, never {@code null}, defaults to {@link com.amazonaws.services.s3.model.Region#US_Standard}
     */
    @Nonnull
    private static Region getRegionFromString(@Nullable String regionName) {
        Region region = null;

        if (regionName == null || regionName.isEmpty()) {
            region = RegionUtils.getRegion(DEFAULT_AMAZON_S3_REGION_NAME);
        }
        // In 0.7, selregion comes from Regions#name
        if (region == null) {
            region = RegionUtils.getRegion(regionName);
        }

        // In 0.6, selregion comes from Regions#valueOf
        if (region == null) {
            region = RegionUtils.getRegion(Regions.valueOf(regionName).getName());
        }

        if (region == null) {
            region = RegionUtils.getRegion(DEFAULT_AMAZON_S3_REGION_NAME);
        }

        if (region == null) {
            throw new IllegalStateException("No AWS Region found for name '" + regionName + "' and default region '" + DEFAULT_AMAZON_S3_REGION_NAME + "'");
        }
        return region;
    }

    @Nonnull
    public static ClientConfiguration getClientConfiguration(@Nonnull ProxyConfiguration proxy, @Nonnull Region region) {
        final ClientConfiguration clientConfiguration = new ClientConfiguration();

        String s3Endpoint = region.getServiceEndpoint(AmazonS3.ENDPOINT_PREFIX);

        if (shouldUseProxy(proxy, s3Endpoint)) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            if (proxy.getUserName() != null) {
                clientConfiguration.setProxyUsername(proxy.getUserName());
                clientConfiguration.setProxyPassword(proxy.getPassword());
            }
        }

        return clientConfiguration;
    }

    private static boolean shouldUseProxy(ProxyConfiguration proxy, String hostname) {
        if(proxy == null) {
            return false;
        }

        boolean shouldProxy = true;
        for(Pattern p : proxy.getNoProxyHostPatterns()) {
            if(p.matcher(hostname).matches()) {
                shouldProxy = false;
                break;
            }
        }

        return shouldProxy;
    }
}
