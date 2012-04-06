package hudson.plugins.s3;

import hudson.model.AbstractBuild;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;

import java.io.IOException;
import java.io.Serializable;

import jenkins.model.Jenkins;

public class FingerprintRecord implements Serializable {
  private static final long serialVersionUID = 1L;
  final boolean produced;
  final String md5sum;
  final S3Artifact artifact;


  public FingerprintRecord(boolean produced, String bucket, String name, String md5sum) {
      this.produced = produced;
      this.artifact = new S3Artifact(bucket, name);
      this.md5sum = md5sum;
  }

  Fingerprint addRecord(AbstractBuild<?,?> build) throws IOException {
      FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
      return map.getOrCreate(produced?build:null, artifact.getName(), md5sum);
  }

  public String getName() {
    return artifact.getName();
  }

  public String getBucket() {
    return artifact.getBucket();
  }

  public String getFingerprint() {
    return md5sum;
  }

}
