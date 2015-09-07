package hudson.plugins.s3.utils;

import hudson.ProxyConfiguration;
import hudson.util.Secret;

import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;

public class S3Utils {

    public static AmazonS3Client createClient(String accessKey, Secret secretKey, boolean useRole) {
        AmazonS3Client client;
        if (useRole) {
            client = new AmazonS3Client(getClientConfiguration());
        } else {
            client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey.getPlainText()), getClientConfiguration());
        }
        return client;
    }

    private static ClientConfiguration getClientConfiguration() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
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
        if (proxy == null) {
            return false;
        }
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
