package hudson.plugins.s3;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.RunAction;
import hudson.model.Run;

public class S3ArtifactsAction implements RunAction {
  private final Run run;
  private final String profile;
  private final List<FingerprintRecord> artifacts;

  public S3ArtifactsAction(Run<?,?> run, S3Profile profile, List<FingerprintRecord> artifacts) {
      this.run = run;
      this.profile = profile.getName();
      this.artifacts = artifacts;
      onLoad();   // make compact
  }

  public Run<?,?> getRun() {
      return run;
  }

  public String getIconFileName() {
    return "fingerprint.png";
  }

  public String getDisplayName() {
    return "S3 Artifacts";
  }

  public String getUrlName() {
    return "s3";
  }

  public void onLoad() {
  }

  public void onAttached(Run r) {
  }

  public void onBuildComplete() {
  }

  public String getProfile() {
    return profile;
  }

  public List<FingerprintRecord> getArtifacts() {
    return artifacts;
  }

  public void doDownload(final StaplerRequest request, final StaplerResponse response) throws IOException, ServletException {

      String restOfPath = request.getRestOfPath();
      if (restOfPath == null) {
          return;
      }

      // skip the leading /
      String artifact = restOfPath.substring(1);
      for (FingerprintRecord record : getArtifacts()) {
          if (record.artifact.getName().equals(artifact)) {
              S3Profile s3 = S3BucketPublisher.getProfile(profile);
              String url = s3.getDownloadURL(run, record);
              response.sendRedirect2(url);
              return;
          }
      }
      response.sendError(404, "This artifact is not available");
  }

}