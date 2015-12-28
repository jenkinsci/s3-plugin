package hudson.plugins.s3.cloudfront.callable;

import hudson.util.Secret;

import java.io.Serializable;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClient;

public class AbstractCloudFrontCallable implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String accessKey;
	private final Secret secretKey;
	private final boolean useRole;
	private transient AmazonCloudFront cloudFrontClient;

	public AbstractCloudFrontCallable(String accessKey, Secret secretKey, boolean useRole) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.useRole = useRole;
	}

	protected AmazonCloudFront getClient() {
		if (cloudFrontClient == null) {
			if (useRole) {
				cloudFrontClient = new AmazonCloudFrontClient();
			} else {
				cloudFrontClient = new AmazonCloudFrontClient(new BasicAWSCredentials(accessKey, secretKey.getPlainText()));
			}
		}
		return cloudFrontClient;
	}
}
