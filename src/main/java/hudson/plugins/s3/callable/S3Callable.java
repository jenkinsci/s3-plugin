package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import hudson.FilePath.FileCallable;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.ClientHelper;
import hudson.util.Secret;
import jenkins.security.Roles;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.remoting.RoleChecker;

import java.io.ObjectStreamException;
import java.util.HashMap;

abstract class S3Callable<T> implements FileCallable<T> {
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;
    private final String region;
    private final ProxyConfiguration proxy;
    private final String customEndpoint;

    private static transient HashMap<String, TransferManager> transferManagers = new HashMap<>();

    S3Callable(String accessKey, Secret secretKey, boolean useRole, String region, ProxyConfiguration proxy) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
        this.region = region;
        this.proxy = proxy;
        this.customEndpoint = ClientHelper.ENDPOINT;
    }

    protected synchronized TransferManager getTransferManager() {
        final String uniqueKey = getUniqueKey();
        if (transferManagers.get(uniqueKey) == null) {
            final AmazonS3 client = ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole, region, proxy, customEndpoint);
            transferManagers.put(uniqueKey, TransferManagerBuilder.standard().withS3Client(client).build());
        }

        return transferManagers.get(uniqueKey);
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {
        roleChecker.check(this, Roles.SLAVE);
    }

    private String getUniqueKey() {
        return region + '_' + secretKey + '_' + accessKey + '_' + useRole;
    }
}