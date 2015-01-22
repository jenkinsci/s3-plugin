package hudson.plugins.s3;

import java.io.Serializable;
import hudson.FilePath;

/**
 * Provides a way to construct a destination bucket name and object name based
 * on the bucket name provided by the user.
 *
 * The convention implemented here is that a / in a bucket name is used to
 * construct a structure in the object name.  That is, a put of file.txt to bucket name
 * of "mybucket/v1" will cause the object "v1/file.txt" to be created in the mybucket.
 *
 */
public class Destination implements Serializable {
    private static final long serialVersionUID = 1L;
    public String bucketName;
    public String userBucketName;
    public String fileName;
    public String objectName;

    /*
     * Create a Destination with unmanaged artifacts
     * @param userBucketName
     * @param fileName
     */
    public Destination(String userBucketName, String fileName) {

        initialize(userBucketName, fileName, "");
    }

    /*
     * Create a Destination with managed artifacts
     * @param projectName
     * @param buildId
     * @param bucketName
     * @param fileName
     */
    public Destination(String projectName, int buildId, String bucketName, String fileName) {

        initialize(bucketName, fileName, getManagedPrefix(projectName, buildId));
    }

    /*
     * Create a Destination with conditionally managed artifacts
     * @param projectName
     * @param buildId
     * @param bucketName
     * @param filePath
     * @param searchPathLength
     * @param artifactManagement
     */
    public Destination(String projectName, int buildId, String bucketName, FilePath filePath,
                       int searchPathLength, String artifactManagement) {

        String fileName;
        if (Entry.isStructured(artifactManagement)) {
            fileName = filePath.getRemote().substring(searchPathLength);
        }
        else {
            fileName = filePath.getName();
        }

        if (Entry.isManaged(artifactManagement)) {
            initialize(bucketName, fileName, getManagedPrefix(projectName, buildId));
        }
        else {
            initialize(bucketName, fileName, "");
        }
    }

    private String getManagedPrefix(String projectName, int buildID) {

        return "jobs/" + projectName + "/" + buildID + "/";
    }

    private void initialize(final String userBucketName, final String fileName, String pathPrefix) {

        if (userBucketName == null || fileName == null)
            throw new IllegalArgumentException("Not defined for null parameters: " + userBucketName + "," + fileName);

        final String[] bucketNameArray = userBucketName.split("/", 2);

        bucketName = bucketNameArray[0];
        this.userBucketName = userBucketName;
        this.fileName = fileName;

        if (bucketNameArray.length > 1) {
            this.objectName = bucketNameArray[1] + "/" + pathPrefix + fileName;
        } else {
            this.objectName = pathPrefix + fileName;
        }
    }

    @Override
    public String toString() {
        return "Destination [bucketName=" + bucketName + ", objectName=" + objectName + "]";
    }

    public String getUserBucketName() {
        return userBucketName;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getFileName() {
        return fileName;
    }
}