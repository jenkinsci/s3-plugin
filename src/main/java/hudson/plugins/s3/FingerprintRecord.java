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
    private boolean showDirectlyInBrowser;


    public FingerprintRecord(boolean produced, String bucket, String name, String region, String md5sum) {
        this.produced = produced;
        this.artifact = new S3Artifact(region, bucket, name);
        this.md5sum = md5sum;
        this.showDirectlyInBrowser = false;
        this.keepForever = false;
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

    public boolean isShowDirectlyInBrowser() {
        return showDirectlyInBrowser;
    }

    public void setShowDirectlyInBrowser(boolean showDirectlyInBrowser) {
        this.showDirectlyInBrowser = showDirectlyInBrowser;
    }

    @Exported
    public String getName() {
        return artifact.getName();
    }

    @Exported
    public String getLink() {
        //Chrome and IE convert backslash in the URL into forward slashes, need escape with %5c
        return artifact.getName().replace("\\","%5C");
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
