package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.plugins.s3.Uploads;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;

public final class S3CleanupUploadCallable implements MasterSlaveCallable<Void> {
    @Override
    public Void invoke(File f, VirtualChannel channel) {
        invoke(new FilePath(f));
        return null;
    }

    @Override
    public Void invoke(FilePath file) {
        Uploads.getInstance().cleanup(file);
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
