package hudson.plugins.s3;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import hudson.FilePath;
import java.io.File;

public class BucketnameTest {

    @Test
    public void testAnythingAfterSlashInBucketNameIsPrependedToObjectName() {

        // Assertions based on the behaviour of toString is maybe fragile but I think
        // reasonably readable.

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=test.txt]",
            new Destination("my-bucket-name", "test.txt").toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=foo/test.txt]",
            new Destination("my-bucket-name/foo", "test.txt").toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=foo/baz/test.txt]",
            new Destination("my-bucket-name/foo/baz", "test.txt").toString() );

        // Unclear if this is the desired behaviour or not:
        assertEquals( "Destination [bucketName=my-bucket-name, objectName=/test.txt]",
            new Destination("my-bucket-name/", "test.txt").toString() );


        assertEquals( "Destination [bucketName=my-bucket-name, objectName=jobs/my-job/10/test.txt]",
                new Destination("my-job", 10, "my-bucket-name", "test.txt").toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=foo/jobs/my-job/10/test.txt]",
                new Destination("my-job", 10, "my-bucket-name/foo", "test.txt").toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=foo/baz/jobs/my-job/10/test.txt]",
                new Destination("my-job", 10, "my-bucket-name/foo/baz", "test.txt").toString() );


        String basePath = "/Some/Jenkins/Workspace/";
        FilePath filePath = new FilePath(new File(basePath + "target/foo/bar/test.txt"));

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=jobs/my-job/10/test.txt]",
                new Destination("my-job", 10, "my-bucket-name", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.MANAGED_FLATTENED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/jobs/my-job/10/test.txt]",
                new Destination("my-job", 10, "my-bucket-name/baz", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.MANAGED_FLATTENED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=jobs/my-job/10/target/foo/bar/test.txt]",
                new Destination("my-job", 10, "my-bucket-name", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.MANAGED_STRUCTURED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/jobs/my-job/10/target/foo/bar/test.txt]",
                new Destination("my-job", 10, "my-bucket-name/baz", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.MANAGED_STRUCTURED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=test.txt]",
                new Destination("my-job", 10, "my-bucket-name", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.UNMANAGED_FLATTENED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/test.txt]",
                new Destination("my-job", 10, "my-bucket-name/baz", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.UNMANAGED_FLATTENED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=target/foo/bar/test.txt]",
                new Destination("my-job", 10, "my-bucket-name", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.UNMANAGED_STRUCTURED.name() ).toString() );

        assertEquals( "Destination [bucketName=my-bucket-name, objectName=baz/target/foo/bar/test.txt]",
                new Destination("my-job", 10, "my-bucket-name/baz", filePath, basePath.length(),
                        Entry.managedArtifactsEnum.UNMANAGED_STRUCTURED.name() ).toString() );
    }

}
