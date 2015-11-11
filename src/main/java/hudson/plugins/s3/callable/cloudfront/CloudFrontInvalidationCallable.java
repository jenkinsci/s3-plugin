package hudson.plugins.s3.callable.cloudfront;

import hudson.FilePath;
import hudson.plugins.s3.Destination;
import hudson.util.Secret;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.CreateInvalidationResult;
import com.amazonaws.services.cloudfront.model.DistributionSummary;
import com.amazonaws.services.cloudfront.model.Invalidation;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.ListDistributionsRequest;
import com.amazonaws.services.cloudfront.model.Paths;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class CloudFrontInvalidationCallable extends AbstractCloudFrontCallable {

	private static final long serialVersionUID = 1L;
	
	private static final Logger log = Logger.getLogger(CloudFrontInvalidationCallable.class.getName());

	private static final String UNIX_SEPARATOR = "/";
	private final String bucket;
	private final int searchPathLength;
	private Function<FilePath, String> filePathToS3KeysTransformer;

	public CloudFrontInvalidationCallable(String accessKey, Secret secretKey,
			boolean useRole, String bucket, int searchPathLength) {
		super(accessKey, secretKey, useRole);
		this.bucket = bucket;
		this.searchPathLength = searchPathLength;
		filePathToS3KeysTransformer = new Function<FilePath, String>() {

			@Override
			public String apply(FilePath filePath) {
				if (!isValidFile(filePath)) {
					return null;
				}
				String fileName = filePath.getRemote().substring(CloudFrontInvalidationCallable.this.searchPathLength);
				Destination dest = new Destination(CloudFrontInvalidationCallable.this.bucket, fileName);

				String key = dest.objectName;
				if (!key.startsWith(UNIX_SEPARATOR)) {
					key = UNIX_SEPARATOR.concat(key);
				}
				return key;
			}

			private boolean isValidFile(FilePath filePath) {
				try {
					return !filePath.isDirectory();
				} catch (Exception e) {
					log.info(String.format("Failed to check filePath %s, %s",
							filePath.getName(), e.toString()));
				}
				return false;
			}

		};
	}

	public void invoke(FilePath...paths) {
		String[] keysToInvalidate = getS3Keys(paths).toArray(new String[paths.length]);
		List<DistributionSummary> distributionitems = getClient().listDistributions(new ListDistributionsRequest()).getDistributionList().getItems();
		for (DistributionSummary distribution: distributionitems){
			if (distribution.isEnabled()){
				Invalidation invalidationResult = invalidateFiles(distribution, keysToInvalidate);
				log.info(String.format(
						"Invalidated cache %s of path %s with status = %s",	distribution.getAliases().toString(),
						invalidationResult.getInvalidationBatch().getPaths().toString(), invalidationResult.getStatus()));
			}
			
		}		
	}

	private List<String> getS3Keys(FilePath[] paths) {
		return Lists.transform(Arrays.asList(paths), filePathToS3KeysTransformer);
	}

	private Invalidation invalidateFiles(DistributionSummary distributionSummary, String[] keysToInvalidate) {
		String distributionId = distributionSummary.getId();
		String callerReference = String.valueOf(System.currentTimeMillis());
		CreateInvalidationResult invalidationResult = getClient().createInvalidation(
						new CreateInvalidationRequest(distributionId,
								new InvalidationBatch(new Paths().withItems(keysToInvalidate)
										.withQuantity(keysToInvalidate.length),	callerReference)));
		return invalidationResult.getInvalidation();
	}
}
