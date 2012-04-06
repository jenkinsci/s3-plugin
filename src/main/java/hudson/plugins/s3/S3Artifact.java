package hudson.plugins.s3;

import java.io.Serializable;

public final class S3Artifact implements Serializable {
  private static final long serialVersionUID = 1L;

  final private String bucket;
  final private String name;
  
  public S3Artifact(String bucket, String name) {
    this.bucket = bucket;
    this.name = name;
  }

  public String getBucket() {
    return bucket;
  }

  public String getName() {
    return name;
  }
}
