package hudson.plugins.s3.utils;

import hudson.ProxyConfiguration;
import hudson.util.Secret;

import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;

public class S3Utils {

    public static AmazonS3Client createClient(String accessKey, Secret secretKey, boolean useRole, boolean useSts, String stsRoleArn) {
        AWSCredentialsProvider credentialsProvider;
        if (useRole) {
            credentialsProvider = new DefaultAWSCredentialsProviderChain();
        } else {
            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey.getPlainText());
            credentialsProvider = new StaticCredentialsProvider(credentials);
        }
        if (useSts) {
            credentialsProvider = new STSAssumeRoleSessionCredentialsProvider(credentialsProvider, stsRoleArn, "foo",
                    getClientConfiguration());
        }
        return new AmazonS3Client(credentialsProvider, getClientConfiguration());
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
