package hudson.plugins.s3;

import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.io.Serializable;

@ExportedBean
public class FingerprintRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean produced;
    private final String md5sum;
    private final S3Artifact artifact;
    private boolean keepForever;


    public FingerprintRecord(boolean produced, String bucket, String name, String region, String md5sum) {
        this.produced = produced;
        this.artifact = new S3Artifact(region, bucket, name);
        this.md5sum = md5sum;
    }

    Fingerprint addRecord(Run<?, ?> run) throws IOException {
        final FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
        return map.getOrCreate(produced ? run : null, artifact.getName(), md5sum);
    }

    public boolean isKeepForever() {
        return keepForever;
    }

    public void setKeepForever(boolean keepForever) {
        this.keepForever = keepForever;
    }

    @Exported
    public String getName() {
        return artifact.getName();
    }

    @Exported
    public String getFingerprint() {
        return md5sum;
    }

    @Exported
    public S3Artifact getArtifact() {
        return artifact;
    }
}
