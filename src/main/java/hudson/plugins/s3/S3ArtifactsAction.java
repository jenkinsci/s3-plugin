package hudson.plugins.s3;

import java.io.File;
import java.util.Date;
import java.util.List;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;

import hudson.model.Run;
import jenkins.model.RunAction2;

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

    /**
     * Generate a signed download request for a redirect from s3/download.
     *
     * When the user asks to download a file, we sign a short-lived S3 URL
     * for them and redirect them to it, so we don't have to proxy for the
     * download and there's no need for the user to have credentials to
     * access S3.
     */
    public String getDownloadURL(AmazonS3Client client, int signedUrlExpirySeconds, Run run, FingerprintRecord record) {
        final Destination dest = Destination.newFromRun(run, record.getArtifact());
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(dest.bucketName, dest.objectName);
        request.setExpiration(new Date(System.currentTimeMillis() + signedUrlExpirySeconds*1000));

        if (!record.isShowDirectlyInBrowser()) {
            // let the browser use the last part of the name, not the full path
            // when saving.
            final ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
            final String fileName = (new File(dest.objectName)).getName().trim();
            headers.setContentDisposition("attachment; filename=\"" + fileName + '"');
            request.setResponseHeaders(headers);
        }

        return client.generatePresignedUrl(request).toExternalForm();
    }
}
