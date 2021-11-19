package hudson.plugins.s3;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.ObjectStreamException;
import java.io.Serializable;

@ExportedBean
public final class S3Artifact implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String bucket;
    private final String name;
    private final String region;
    private /*almost final*/ Boolean useFullProjectName;

    public S3Artifact(String region, String bucket, String name) {
        this.bucket = bucket.intern();
        this.name = name.intern();
        this.region = region.intern();
        this.useFullProjectName = true;
    }

    /* Old version of this plugin used short name,
     * so jobs with same name (but from different folders)
     * could replace each other.
     * That's why we started to use full name.
     * But we need to keep backport capability */
    private void readObjectNoData() throws ObjectStreamException {
        this.useFullProjectName = false;
    }

    @Exported
    public String getBucket() {
        return bucket;
    }

    @Exported
    public String getName() {
        return name;
    }

    @Exported
    public String getRegion() {
        return region;
    }

    public Boolean useFullProjectName() {
        if (useFullProjectName == null)
            return false;

        return useFullProjectName;
    }
}