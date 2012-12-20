package jenkins.plugins.s3;

import hudson.FilePath;
import hudson.Util;
import hudson.model.BuildListener;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class S3Profile {
    private String name;
    private String accessKey;
    private String secretKey;
    private static final AtomicReference<AmazonS3Client> client = new AtomicReference<AmazonS3Client>(
            null);

    public S3Profile() {
    }

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey) {
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        client.set(new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey)));
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public final String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public final String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AmazonS3Client getClient() {
        if (client.get() == null) {
            client.set(new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey)));
        }
        return client.get();
    }

    public void check() throws Exception {
        getClient().listBuckets();
    }

    public void publish(PublishRule rule, FilePath workspace, BuildListener listener,
            Map<String, String> envVars) throws IOException, InterruptedException {
        FilePath workingDir = workspace.child(rule.getWorkingDir());
        FilePath[] files = workingDir.list(rule.getInclude(), rule.getExclude());

        listener.getLogger().println("Preparing to copy " + files.length + " file(s)");

        for (FilePath file : files) {
            int i = 0;
            while (i < file.getRemote().length() && i < workingDir.getRemote().length()
                    && file.getRemote().charAt(i) == workingDir.getRemote().charAt(i)) {
                i++;
            }

            listener.getLogger().println(
                    "Copying " + workingDir.getRemote() + " : " + file.getRemote().substring(i)
                            + " -> " + rule.getTo());
            try {
                String bucket = Util.replaceMacro(rule.getTo(), envVars);
                String destKey = "";

                int bucketSeperaterIndex = bucket.indexOf("/");
                if (bucketSeperaterIndex > 0) {
                    destKey = bucket.substring(bucketSeperaterIndex + 1);
                    bucket = bucket.substring(0, bucketSeperaterIndex);
                }

                destKey = destKey + file.getRemote().substring(i);

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(Mimetypes.getInstance().getMimetype(file.getName()));
                metadata.setContentLength(file.length());
                getClient().putObject(bucket, destKey, file.read(), metadata);

            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                listener.getLogger().println("Continuing with the other files");
            }
        }
    }
}
