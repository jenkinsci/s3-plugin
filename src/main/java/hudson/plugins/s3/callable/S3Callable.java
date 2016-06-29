package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.FilePath.FileCallable;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.ClientHelper;
import hudson.util.Secret;
import org.jenkinsci.remoting.RoleChecker;

import java.util.HashMap;

abstract class S3Callable<T> implements FileCallable<T> {
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;
    private final String region;
    private final ProxyConfiguration proxy;

    private static transient HashMap<String, TransferManager> transferManagers = new HashMap<>();

    S3Callable(String accessKey, Secret secretKey, boolean useRole, String region, ProxyConfiguration proxy) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
        this.region = region;
        this.proxy = proxy;
    }

    protected synchronized TransferManager getTransferManager() {
        final String uniqueKey = getUniqueKey();
        if (transferManagers.get(uniqueKey) == null) {
            final AmazonS3 client = ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole, region, proxy);
            transferManagers.put(uniqueKey, new TransferManager(client));
        }

        return transferManagers.get(uniqueKey);
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {

    }

    private String getUniqueKey() {
        return region + '_' + secretKey + '_' + accessKey + '_' + useRole;
    }
}