package hudson.plugins.s3;

public final class Entry {
    /**
     * Destination bucket for the copy. Can contain macros. 
     */
    public String bucket;
    /**
     * File name relative to the workspace root to upload.
     * Can contain macros and wildcards.
     * <p>
     */
    public String sourceFile;
    
    /**
     * Upload either from the slave or the master
     */
    public boolean uploadFromSlave;
}
