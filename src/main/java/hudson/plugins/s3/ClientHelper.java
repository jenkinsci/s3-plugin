package hudson.plugins.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import hudson.util.Secret;

public class ClientHelper {
    public static AmazonS3Client createClient(String accessKey, Secret secretKey, boolean useRole)
    {
        if (useRole) {
            return new AmazonS3Client();
        } else {
            return new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey.getPlainText()));
        }
    }
}
