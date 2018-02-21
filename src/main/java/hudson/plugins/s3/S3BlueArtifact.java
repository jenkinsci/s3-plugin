package hudson.plugins.s3;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.factory.BlueArtifactFactory;
import io.jenkins.blueocean.rest.hal.Link;
import io.jenkins.blueocean.rest.model.BlueArtifact;

import java.util.Collection;

public final class S3BlueArtifact extends BlueArtifact {

    private final S3ArtifactsAction action;
    private final S3Artifact artifact;

    private S3BlueArtifact(S3ArtifactsAction action, S3Artifact artifact, Link parent) {
        super(parent);
        this.action = action;
        this.artifact = artifact;
    }

    @Override
    public String getName() {
        return artifact.getName();
    }

    @Override
    public String getPath() {
        return artifact.getName();
    }

    @Override
    public String getUrl() {
        // TODO: URL to artifact - needs testing
        return String.format("/%s%s", action.getUrlName(), artifact.getName());
    }

    @Override
    public long getSize() {
        return -1; // Cant determine size of remote artifact (perhaps this could be stored on S3Artifact?
    }

    @Override
    public boolean isDownloadable() {
        return true;
    }

    @Extension
    public static final class FactoryImpl extends BlueArtifactFactory {
        @Override
        public Collection<BlueArtifact> getArtifacts(Run<?, ?> run, final Reachable reachable) {
            final S3ArtifactsAction action = run.getAction(S3ArtifactsAction.class);
            if (action == null) {
                return null;
            }
            return Collections2.transform(action.getArtifacts(), new Function<FingerprintRecord, BlueArtifact>() {
                @Override
                public BlueArtifact apply(FingerprintRecord input) {
                    S3Artifact artifact = input.getArtifact();
                    return new S3BlueArtifact(action, artifact, reachable.getLink().rel(Util.encode(artifact.getName() + "+" + artifact.getBucket())));
                }
            });
        }
    }
}
