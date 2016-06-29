package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;

public interface MasterSlaveCallable<T> extends FilePath.FileCallable<T>  {
    /**
     * Upload from slave directly
     */
    @Override
    T invoke(File file, VirtualChannel channel) throws IOException, InterruptedException;

    /**
     * Stream from slave to master, then upload from master
     */
    T invoke(FilePath file) throws IOException, InterruptedException;
}
