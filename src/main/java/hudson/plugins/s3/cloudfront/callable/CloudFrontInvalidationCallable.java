package hudson.plugins.s3.cloudfront.callable;

import hudson.plugins.s3.cloudfront.InvalidationRecord;
import hudson.util.Secret;

import java.util.List;
import java.util.logging.Logger;

import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.CreateInvalidationResult;
import com.amazonaws.services.cloudfront.model.DistributionSummary;
import com.amazonaws.services.cloudfront.model.Invalidation;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.ListDistributionsRequest;
import com.amazonaws.services.cloudfront.model.Origin;
import com.amazonaws.services.cloudfront.model.Paths;
import com.amazonaws.util.SdkHttpUtils;

public class CloudFrontInvalidationCallable extends AbstractCloudFrontCallable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(CloudFrontInvalidationCallable.class
            .getName());
    private static final String UNIX_SEPARATOR = "/";

    public CloudFrontInvalidationCallable(String accessKey, Secret secretKey, boolean useRole) {
        super(accessKey, secretKey, useRole);
    }

    public InvalidationRecord invoke(String bucket, String invalidationPath) {
        String normalizedPaths = normalize(invalidationPath);
        List<DistributionSummary> distributionitems = getClient()
                .listDistributions(new ListDistributionsRequest()).getDistributionList().getItems();
        InvalidationRecord invalidationRecord = new InvalidationRecord();
        for (DistributionSummary distribution : distributionitems) {
            if (isDistributionRelatedToBucket(distribution, bucket) && distribution.isEnabled()) {
                Invalidation invalidationResult = invalidateFiles(distribution, normalizedPaths);
                invalidationRecord.add(distribution, invalidationPath);
                log.info(String.format("Invalidated cache %s of path %s with status = %s",
                        distribution.getAliases().toString(), invalidationResult
                                .getInvalidationBatch().getPaths().toString(),
                        invalidationResult.getStatus()));
            }
        }
        return invalidationRecord;
    }

    private boolean isDistributionRelatedToBucket(DistributionSummary distribution, String bucket) {
        List<Origin> origins = distribution.getOrigins().getItems();
        for (Origin origin : origins) {
            if (origin.getDomainName().startsWith(bucket)) {
                return true;
            }
        }
        return false;
    }

    private String encode(String path) {
        int starIndex = path.indexOf("*");
        if (starIndex >=0){
            return String.format("%s*%s", encode(path.substring(0, starIndex)), encode(path.substring(starIndex+1)));
        }
        return SdkHttpUtils.urlEncode(path, true);
    }

    private String normalize(String path) {
        String normalizedKey = encode(path);
        if (!normalizedKey.startsWith(UNIX_SEPARATOR)) {
            normalizedKey = UNIX_SEPARATOR.concat(normalizedKey);
        }
        return normalizedKey;
    }

    private Invalidation invalidateFiles(DistributionSummary distributionSummary, String normalizedPath) {
        String distributionId = distributionSummary.getId();
        String callerReference = String.valueOf(System.currentTimeMillis());
        CreateInvalidationResult invalidationResult = getClient().createInvalidation(
                new CreateInvalidationRequest(distributionId, new InvalidationBatch(new Paths()
                        .withItems(normalizedPath).withQuantity(1), callerReference)));
        return invalidationResult.getInvalidation();
    }
}
