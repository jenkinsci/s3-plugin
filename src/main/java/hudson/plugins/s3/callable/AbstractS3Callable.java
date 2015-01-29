package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3Client;

import java.io.Serializable;

public class AbstractS3Callable implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient AmazonS3Client client;

    public AbstractS3Callable(AmazonS3Client client) {
        this.client = client;
    }

    protected AmazonS3Client getClient() {
//        if (client == null) {
//            if (useRole) {
//                client = new AmazonS3Client();
//            } else {
//                client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey.getPlainText()));
//            }
//        }
        return client;
    }

}
