package hudson.plugins.s3;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;

import java.util.List;

public class S3ArtifactsProjectAction implements Action {

    private final AbstractProject<?, ?> project;

    public S3ArtifactsProjectAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    private Run getLastSuccessfulBuild() {
        return project.getLastSuccessfulBuild();
    }

    @SuppressWarnings("unused")
    public S3ArtifactsAction getLatestDeployedArtifacts() {
        Run latestSuccessfulBuild = getLastSuccessfulBuild();
        if (latestSuccessfulBuild == null) {
            return null;
        }
        List<S3ArtifactsAction> actions = latestSuccessfulBuild.getActions(S3ArtifactsAction.class);
        if (actions == null || actions.size() == 0) {
            return null;
        }
        return actions.get(actions.size() - 1);
    }

    @SuppressWarnings("unused")
    public int getLastSuccessfulNumber() {
        Run latestSuccessfulBuild = getLastSuccessfulBuild();
        if (latestSuccessfulBuild == null) {
            return 0;
        }
        return latestSuccessfulBuild.getNumber();
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}