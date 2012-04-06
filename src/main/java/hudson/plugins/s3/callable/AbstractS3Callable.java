package hudson.plugins.s3.callable;

import java.io.Serializable;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;

public abstract class AbstractS3Callable implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final String secretKey;
    private AmazonS3Client client;

    public AbstractS3Callable(String accessKey, String secretKey) 
    {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    protected AmazonS3Client getClient() 
    {
        if (client == null) {
            client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
        }
        return client;
    }

}