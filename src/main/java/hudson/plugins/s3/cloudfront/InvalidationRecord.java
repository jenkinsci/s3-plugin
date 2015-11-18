package hudson.plugins.s3.cloudfront;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.cloudfront.model.DistributionSummary;

public class InvalidationRecord {

	List<InvalidationRecordEntry> entries = new ArrayList<InvalidationRecordEntry>();

	public void add(DistributionSummary distribution, List<String> paths) {
		entries.add(new InvalidationRecordEntry(distribution, paths));
	}

	@Override
	public String toString() {
		return "InvalidationDetails [" + entries + "]";
	}

	private class InvalidationRecordEntry {

		private DistributionSummary distribution;
		private List<String> paths;

		public InvalidationRecordEntry(DistributionSummary distribution, List<String> paths) {
			this.distribution = distribution;
			this.paths = paths;
		}

		@Override
		public String toString() {
			return "[distribution=" + distribution.getAliases().getItems() + ", paths=" + paths + "]";
		}
		
		
	}
}
