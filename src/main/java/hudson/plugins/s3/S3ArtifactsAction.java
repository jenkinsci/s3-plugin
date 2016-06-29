package hudson.plugins.s3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.Run;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

@ExportedBean
public class S3ArtifactsAction implements RunAction2 {
    private final Run build; // Compatibility for old versions
    private final String profile;
    private final List<FingerprintRecord> artifacts;

    public S3ArtifactsAction(Run<?, ?> run, S3Profile profile, List<FingerprintRecord> artifacts) {
        this.build = run;
        this.profile = profile.getName();
        this.artifacts = artifacts;
        onLoad(run);   // make compact
    }

    public Run<?, ?> getBuild() {
        return build;
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

    @Override
    public void onLoad(Run<?, ?> r) {
    }

    public void onAttached(Run r) {
    }

    public String getProfile() {
        return profile;
    }

    @Exported
    public List<FingerprintRecord> getArtifacts() {
        return artifacts;
    }

    public void doDownload(final StaplerRequest request, final StaplerResponse response) throws IOException, ServletException {
        final String restOfPath = request.getRestOfPath();
        if (restOfPath == null) {
            return;
        }

        // skip the leading /
        final String artifact = restOfPath.substring(1);
        for (FingerprintRecord record : artifacts) {
            if (record.getArtifact().getName().equals(artifact)) {
                final S3Profile s3 = S3BucketPublisher.getProfile(profile);
                final String url = s3.getDownloadURL(build, record);
                response.sendRedirect2(url);
                return;
            }
        }
        response.sendError(SC_NOT_FOUND, "This artifact is not available");
    }

}