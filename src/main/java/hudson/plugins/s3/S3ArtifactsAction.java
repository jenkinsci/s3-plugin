package hudson.plugins.s3;

import hudson.Functions;
import hudson.model.Run;
import hudson.util.Secret;
import jakarta.servlet.ServletException;
import jenkins.model.RunAction2;
import jenkins.security.FIPS140;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

@ExportedBean
public class S3ArtifactsAction implements RunAction2 {
    private final Run<?,?> build; // Compatibility for old versions
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
        return hasAccess() ? "fingerprint.png" : null;
    }

    public String getDisplayName() {
        return "S3 Artifacts";
    }

    public String getUrlName() {
        return hasAccess() ? "s3" : null;
    }

    private boolean hasAccess () {
        return !Functions.isArtifactsPermissionEnabled() || build.getParent().hasPermission(Run.ARTIFACTS);
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
        if (!hasAccess()) {
            return Collections.emptyList();
        }
        return artifacts;
    }

    public void doDownload(final StaplerRequest2 request, final StaplerResponse2 response) throws IOException, ServletException {
        if (Functions.isArtifactsPermissionEnabled()) {
            build.getParent().checkPermission(Run.ARTIFACTS);
        }
        final String restOfPath = request.getRestOfPath();
        if (restOfPath == null) {
            return;
        }

        // skip the leading /
        final String artifact = restOfPath.substring(1);
        for (FingerprintRecord record : artifacts) {
            if (record.getArtifact().getName().equals(artifact)) {
                final S3Profile s3 = S3BucketPublisher.getProfile(profile);
                final var client = s3.getClient(record.getArtifact().getRegion());
                final String url = getDownloadURL(client, s3, build, record);
                response.sendRedirect2(url);
                return;
            }
        }
        response.sendError(SC_NOT_FOUND, "This artifact is not available");
    }

    /**
     * Generate a signed download request for a redirect from s3/download.
     *
     * When the user asks to download a file, we sign a short-lived S3 URL
     * for them and redirect them to it, so we don't have to proxy for the
     * download and there's no need for the user to have credentials to
     * access S3.
     */
    private String getDownloadURL(S3Client client, S3Profile s3, Run run, FingerprintRecord record) {
        final Destination dest = Destination.newFromRun(run, record.getArtifact());
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .fipsEnabled(FIPS140.useCompliantAlgorithms())
                .s3Client(client)
                .region(Region.of(record.getArtifact().getRegion()));
        if (ClientHelper.ENDPOINT_URI != null) {
            presignerBuilder.endpointOverride(ClientHelper.ENDPOINT_URI);
            presignerBuilder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        if (!s3.isUseRole()) {
            presignerBuilder.credentialsProvider(() -> AwsBasicCredentials.create(s3.getAccessKey(), Secret.toString(s3.getSecretKey())));
        }
        try (S3Presigner presigner = presignerBuilder.build()) {
            GetObjectRequest.Builder builder = GetObjectRequest.builder().bucket(dest.bucketName).key(dest.objectName);
            if (!record.isShowDirectlyInBrowser()) {
                // let the browser use the last part of the name, not the full path
                // when saving.
                final String fileName = (new File(dest.objectName)).getName().trim();
                builder.responseContentDisposition(fileName);
            }

            GetObjectRequest getObjectRequest = builder.build();
            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(s3.getSignedUrlExpirySeconds()))
                    .getObjectRequest(getObjectRequest).build();

            return presigner.presignGetObject(getObjectPresignRequest).url().toExternalForm();

        }
    }
}
