package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.plugins.s3.Uploads;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

public final class S3WaitUploadCallable implements MasterSlaveCallable<Void> {

    private final int uploadTimeout;

    public S3WaitUploadCallable(int uploadTimeout) {
        this.uploadTimeout = uploadTimeout;
    }

    @Override
    public Void invoke(File f, VirtualChannel channel) throws InterruptedException, IOException {
        invoke(new FilePath(f));
        return null;
    }

    @Override
    public Void invoke(FilePath file) throws InterruptedException, IOException {
        Uploads.getInstance().finishUploading(file, uploadTimeout);
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
