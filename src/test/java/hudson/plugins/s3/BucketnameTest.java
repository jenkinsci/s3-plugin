package hudson.plugins.s3;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import hudson.FilePath;
import java.io.File;

public class BucketnameTest {

    @Test
    public void testUnmanagedJobs() {

        // Assertions based on the behaviour of toString is maybe fragile but I think
        // reasonably readable.

        Destination dest;

        dest = new Destination("my-bucket-name", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=test.txt]", dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("test.txt", dest.getObjectName());

        dest = new Destination("my-bucket-name/foo", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=foo/test.txt]", dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name/foo", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("foo/test.txt", dest.getObjectName());

        dest = new Destination("my-bucket-name/foo/baz", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=foo/baz/test.txt]", dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name/foo/baz", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("foo/baz/test.txt", dest.getObjectName());

        // Unclear if this is the desired behaviour or not:
        dest = new Destination("my-bucket-name/", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=/test.txt]", dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name/", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("/test.txt", dest.getObjectName());
    }

    @Test
    public void testManagedJobs() {

        int jobId = 10;
        Destination dest;

        dest = new Destination("my-job", jobId, "my-bucket-name", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=jobs/my-job/10/test.txt]",
                dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("jobs/my-job/10/test.txt", dest.getObjectName());


        dest = new Destination("my-job", jobId, "my-bucket-name/foo", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=foo/jobs/my-job/10/test.txt]",
                dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name/foo", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("foo/jobs/my-job/10/test.txt", dest.getObjectName());


        dest = new Destination("my-job", jobId, "my-bucket-name/foo/baz", "test.txt");
        assertEquals("Destination [bucketName=my-bucket-name, objectName=foo/baz/jobs/my-job/10/test.txt]",
                dest.toString());
        assertEquals("my-bucket-name", dest.getBucketName());
        assertEquals("my-bucket-name/foo/baz", dest.getUserBucketName());
        assertEquals("test.txt", dest.getFileName());
        assertEquals("foo/baz/jobs/my-job/10/test.txt", dest.getObjectName());
    }

    @Test
    public void testConditionallyManagedJobs() {

        Destination dest;
        int jobId = 10;
        String basePath = "/The/Project/Workspace/";
        int basePathLen = basePath.length();
        FilePath filePath = new FilePath(new File(basePath + "target/foo/bar/test.txt"));

        dest = new Destination("my-job", jobId, "my-bucket-name", filePath, basePathLen,
                Entry.managedArtifactsEnum.MANAGED_FLATTENED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=jobs/my-job/10/test.txt]", dest.toString() );
        assertEquals( "test.txt", dest.getFileName() );
        assertEquals( "jobs/my-job/10/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name/baz", filePath, basePathLen,
                Entry.managedArtifactsEnum.MANAGED_FLATTENED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/jobs/my-job/10/test.txt]", dest.toString() );
        assertEquals( "test.txt", dest.getFileName() );
        assertEquals( "baz/jobs/my-job/10/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name/baz", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name", filePath, basePathLen,
                Entry.managedArtifactsEnum.MANAGED_STRUCTURED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=jobs/my-job/10/target/foo/bar/test.txt]",
                dest.toString() );
        assertEquals( "target/foo/bar/test.txt", dest.getFileName() );
        assertEquals( "jobs/my-job/10/target/foo/bar/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name/baz", filePath, basePathLen,
                Entry.managedArtifactsEnum.MANAGED_STRUCTURED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/jobs/my-job/10/target/foo/bar/test.txt]",
                 dest.toString() );
        assertEquals( "target/foo/bar/test.txt", dest.getFileName() );
        assertEquals( "baz/jobs/my-job/10/target/foo/bar/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name/baz", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name", filePath, basePathLen,
                Entry.managedArtifactsEnum.UNMANAGED_FLATTENED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=test.txt]",
                 dest.toString() );
        assertEquals( "test.txt", dest.getFileName() );
        assertEquals( "test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name/baz", filePath, basePathLen,
                Entry.managedArtifactsEnum.UNMANAGED_FLATTENED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/test.txt]",
                 dest.toString() );
        assertEquals( "test.txt", dest.getFileName() );
        assertEquals( "baz/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name/baz", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name", filePath, basePathLen,
                Entry.managedArtifactsEnum.UNMANAGED_STRUCTURED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=target/foo/bar/test.txt]",
                 dest.toString() );
        assertEquals( "target/foo/bar/test.txt", dest.getFileName() );
        assertEquals( "target/foo/bar/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name", dest.getUserBucketName());


        dest = new Destination("my-job", jobId, "my-bucket-name/baz", filePath, basePathLen,
                Entry.managedArtifactsEnum.UNMANAGED_STRUCTURED.name()
        );
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/target/foo/bar/test.txt]",
                 dest.toString() );
        assertEquals( "target/foo/bar/test.txt", dest.getFileName() );
        assertEquals( "baz/target/foo/bar/test.txt", dest.getObjectName() );
        assertEquals( "my-bucket-name", dest.getBucketName());
        assertEquals( "my-bucket-name/baz", dest.getUserBucketName());
    }

}
