package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.ClientHelper;
import hudson.plugins.s3.S3Profile;
import org.jenkinsci.remoting.RoleChecker;

import java.util.HashMap;

abstract class S3Callable<T> implements FileCallable<T> {
    private static final long serialVersionUID = 1L;

    private final S3Profile profile;
    private final String region;

    private static transient HashMap<String, TransferManager> transferManagers = new HashMap<>();

    S3Callable(S3Profile profile, String region) {
        this.profile = profile;
        this.region = region;
    }

    protected synchronized TransferManager getTransferManager() {
        final String uniqueKey = getUniqueKey();
        if (transferManagers.get(uniqueKey) == null) {
            final AmazonS3 client = ClientHelper.createClient(profile);
            transferManagers.put(uniqueKey, new TransferManager(client));
        }

        return transferManagers.get(uniqueKey);
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {

    }

    private String getUniqueKey() {
        return region + '_' + profile.getSecretKey() + '_' + profile.getAccessKey() + '_' + profile.isUseRole();
    }
}
