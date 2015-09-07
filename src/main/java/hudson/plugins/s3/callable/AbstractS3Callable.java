package hudson.plugins.s3.callable;

import hudson.plugins.s3.utils.S3Utils;
import hudson.util.Secret;

import java.io.Serializable;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3Client;

public class AbstractS3Callable implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;
    private final boolean useSts;
    private final String stsRoleArn;
    private transient AmazonS3Client client;

    public AbstractS3Callable(String accessKey, Secret secretKey, boolean useRole, boolean useSts, String stsRoleArn) 
    {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
        this.useSts = useSts;
        this.stsRoleArn = stsRoleArn;
    }

    protected AmazonS3Client getClient() 
    {
        if (client == null) {
            client = S3Utils.createClient(accessKey, secretKey, useRole, useSts, stsRoleArn);
        }
        return client;
    }
}
