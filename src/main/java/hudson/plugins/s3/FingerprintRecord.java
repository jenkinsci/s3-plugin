package hudson.plugins.s3;

import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.Serializable;

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

  Fingerprint addRecord(Run<?,?> run) throws IOException {
      FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
      return map.getOrCreate(produced ? run:null, artifact.getName(), md5sum);
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
