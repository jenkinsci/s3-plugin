package hudson.plugins.s3.cloudfront;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.amazonaws.services.cloudfront.model.DistributionSummary;

public class InvalidationRecord {

	List<InvalidationRecordEntry> entries = new ArrayList<InvalidationRecordEntry>();

	public void add(DistributionSummary distribution, String...invalidationPath) {
		entries.add(new InvalidationRecordEntry(distribution, invalidationPath));
	}

	@Override
	public String toString() {
		return "InvalidationDetails [" + entries + "]";
	}

	private class InvalidationRecordEntry {

		private DistributionSummary distribution;
		private String[] path;

		public InvalidationRecordEntry(DistributionSummary distribution, String...invalidationPath) {
			this.distribution = distribution;
			this.path = invalidationPath;
		}

		@Override
		public String toString() {
			return "[distribution=" + distribution.getAliases().getItems() + ", paths=" + Arrays.toString(path) + "]";
		}
		
		
	}
}
