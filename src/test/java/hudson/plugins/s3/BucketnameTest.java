package hudson.plugins.s3;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
  
  }

  @Test
  public void testWindowsPathsConvertingToS3CompatiblePaths() {
    assertEquals("Destination [bucketName=my-bucket, objectName=with-some/subfolder//path-from/windows.txt]",
            new Destination("my-bucket/with-some/subfolder/", "path-from\\windows.txt").toString() );
  }

}
