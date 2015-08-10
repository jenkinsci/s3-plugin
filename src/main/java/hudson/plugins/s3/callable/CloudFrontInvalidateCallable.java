package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.InvalidationRecord;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;

import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.CreateInvalidationResult;
import com.amazonaws.services.cloudfront.model.DistributionSummary;
import com.amazonaws.services.cloudfront.model.Invalidation;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.ListDistributionsRequest;
import com.amazonaws.services.cloudfront.model.Paths;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class CloudFrontInvalidateCallable extends AbstractCloudFrontCallable implements FileCallable<InvalidationRecord> {
	
	private static final String UNIX_SEPARATOR = "/";
	private static final Logger log = Logger.getLogger(CloudFrontInvalidateCallable.class.getName());
	private static final long serialVersionUID = -8546203153266622436L;
	private boolean invalidateAfterUpload;
	private final String bucket;
	private final int searchPathLength;
	private Function<FilePath, String> filePathToS3KeysTransformer;

	public CloudFrontInvalidateCallable(String accessKey, Secret secretKey, boolean useRole, String bucket, int searchPathLength, boolean invalidateAfterUpload){
		super(accessKey, secretKey, useRole);
		this.bucket = bucket;
		this.searchPathLength = searchPathLength;
		this.invalidateAfterUpload = invalidateAfterUpload;
		filePathToS3KeysTransformer = new Function<FilePath, String>(){

			@Override
			public String apply(FilePath filePath) {
				if (!isValidFile(filePath)) {
					return null;
				}
		        String fileName = filePath.getRemote().substring(CloudFrontInvalidateCallable.this.searchPathLength);
		        Destination dest = new Destination(CloudFrontInvalidateCallable.this.bucket, fileName);
		        
		        String key = dest.objectName;
		        if (!key.startsWith(UNIX_SEPARATOR)){
		        	key = UNIX_SEPARATOR.concat(key);
		        }
				return key;
			}

			private boolean isValidFile(FilePath filePath) {
				try {
					return !filePath.isDirectory();
				} catch (IOException e) {
					log.info(String.format("Failed to check filePath %s, %s", filePath.getName(),  e.toString()));
				} catch (InterruptedException e) {
					log.info(String.format("Failed to check filePath %s, %s", filePath.getName(),  e.toString()));
				}
				return false;
			}
			
		};
	}

	public InvalidationRecord invoke(FilePath...paths) throws InterruptedException {
		if (!invalidateAfterUpload){
			return null;
		}
		
		String[] keysToInvalidate = getS3Keys(paths).toArray(new String[paths.length]);
		List<DistributionSummary> distributionitems = getClient().listDistributions(new ListDistributionsRequest()).getDistributionList().getItems();
		InvalidationRecord invalidationResult = new InvalidationRecord();	
		for (DistributionSummary distribution: distributionitems){
			invalidationResult.addInvalidation(distribution.getId(), invalidateFiles(distribution, keysToInvalidate));
		}		
		return invalidationResult;
	}

	@Override
	public InvalidationRecord invoke(File f, VirtualChannel channel) throws IOException,
			InterruptedException {
		return invoke(new FilePath(f));
	}

	private Invalidation invalidateFiles(DistributionSummary distributionSummary, String...keysToInvalidate) throws InterruptedException {
		Invalidation invalidation = null;
		if (distributionSummary.isEnabled()){
			String distributionId = distributionSummary.getId();
			String callerReference = String.valueOf(System.currentTimeMillis());
			CreateInvalidationResult invalidationResult = getClient().createInvalidation(new CreateInvalidationRequest(distributionId, new InvalidationBatch(new Paths().withItems(keysToInvalidate).withQuantity(keysToInvalidate.length), callerReference)));
			invalidation = invalidationResult.getInvalidation();
			log.info(String.format("Finished invalidation cache %s of path %s with status = %s", distributionSummary.getAliases().toString(),  invalidation.getInvalidationBatch().getPaths().toString(), invalidation.getStatus()));
		}
		return invalidation;
	}

	private List<String> getS3Keys(FilePath[] paths) {
		List<String> s3Keys = Lists.transform(Arrays.asList(paths), filePathToS3KeysTransformer);
		return s3Keys;
		
	}
		
}
