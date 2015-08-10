package hudson.plugins.s3;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.cloudfront.model.Invalidation;

public class InvalidationRecord implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1386409082143642574L;

	private Map<String, Invalidation> distributionInvalidationMap = new HashMap<String, Invalidation>();

	public void addInvalidation(String distributionId, Invalidation invalidation) {
		this.distributionInvalidationMap.put(distributionId, invalidation);
	}

	public Map<String, Invalidation> getDistributionInvalidationMap() {
		return distributionInvalidationMap;
	}

	
}
