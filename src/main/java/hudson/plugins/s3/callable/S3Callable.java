package hudson.plugins.s3.callable;

import hudson.FilePath.FileCallable;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.ClientHelper;
import hudson.plugins.s3.Uploads;
import hudson.util.Secret;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

abstract class S3Callable<T> implements FileCallable<T> {
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;
    private final String region;
    private final ProxyConfiguration proxy;
    private final String customEndpoint;

    private static transient HashMap<String, S3TransferManager> transferManagers = new HashMap<>();

    S3Callable(String accessKey, Secret secretKey, boolean useRole, String region, ProxyConfiguration proxy) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
        this.region = region;
        this.proxy = proxy;
        this.customEndpoint = ClientHelper.ENDPOINT;
    }

    protected synchronized S3TransferManager getTransferManager() {
        final String uniqueKey = getUniqueKey();
        if (transferManagers.get(uniqueKey) == null) {
            try {
                final var client = ClientHelper.createAsyncClient(accessKey, Secret.toString(secretKey), useRole, region, proxy, new URI(customEndpoint), (long) Uploads.MULTIPART_UPLOAD_THRESHOLD);
                transferManagers.put(uniqueKey, S3TransferManager.builder().s3Client(client).build());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
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