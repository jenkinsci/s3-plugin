/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.s3;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.plugins.copyartifact.LastCompletedBuildSelector;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MinIOTest {

    private static final String ACCESS_KEY = "supersecure";
    private static final String SECRET_KEY = "donttell";
    private static final String CONTAINER_NAME = "jenkins";
    private static final String CONTAINER_PREFIX = "ci/";
    private static final String REGION = "local";
    public static final String FILE_CONTENT = "Hello World";

    private static GenericContainer minioServer;
    private static String minioServiceEndpoint;

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule().javaOptions("-Xmx256m",
            "-Dhudson.plugins.s3.ENDPOINT=http://" + minioServiceEndpoint);

    @BeforeClass
    public static void setUpClass() throws Exception {
        try {
            DockerClientFactory.instance().client();
        } catch (Exception x) {
            Assume.assumeNoException("does not look like Docker is available", x);
        }
        int port = 9000;
        minioServer = new GenericContainer("minio/minio")
                .withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
                .withEnv("MINIO_SECRET_KEY", SECRET_KEY)
                .withCommand("server /data")
                .withExposedPorts(port)
                .waitingFor(new HttpWaitStrategy()
                        .forPath("/minio/health/ready")
                        .forPort(port)
                        .withStartupTimeout(Duration.ofSeconds(10)));
        minioServer.start();

        Integer mappedPort = minioServer.getFirstMappedPort();
        Testcontainers.exposeHostPorts(mappedPort);
        minioServiceEndpoint = String.format("%s:%s", minioServer.getContainerIpAddress(), mappedPort);

        final AmazonS3 client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://" + minioServiceEndpoint, "us-east-1"))
                .withPathStyleAccessEnabled(true)
                .build();

        if (!client.doesBucketExistV2("test")) {
            client.createBucket("test");
            assertTrue(client.doesBucketExistV2("test"));
        }
    }

    @AfterClass
    public static void shutDownClass() {
        if (minioServer != null && minioServer.isRunning()) {
            minioServer.stop();
        }
    }

    @Test
    public void testS3BucketPublisher() throws Throwable {
        final String endpoint = minioServiceEndpoint;
        rr.then(r -> {
            /*r.jenkins.setLabelString("work"); //Able to debug when running on the controller but not an agent
            r.jenkins.setNumExecutors(1);*/
            r.createOnlineSlave(Label.get("work"));
            createProfile();
            createAndRunPublisher(r);
        });
    }

    private static void createAndRunPublisher(final JenkinsRule r) throws Exception {
        final FreeStyleProject job = r.createFreeStyleProject("publisherJob");
        job.setAssignedLabel(Label.get("work"));
        job.getBuildersList().add(new CreateFileBuilder("test.txt", FILE_CONTENT));
        job.getPublishersList().add(new S3BucketPublisher("Local",
                Collections.singletonList(new Entry("test",
                        "test.txt",
                        "",
                        null,
                        "",
                        false,
                        true,
                        true,
                        false,
                        false,
                        false,
                        true,
                        false,
                        Collections.emptyList(),
                        Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                "FINE",
                null,
                false));
        r.buildAndAssertSuccess(job);
    }

    private static void createProfile() {
        final S3BucketPublisher.DescriptorImpl descriptor = ExtensionList.lookup(S3BucketPublisher.DescriptorImpl.class).get(0);
        descriptor.replaceProfiles(Collections.singletonList(new S3Profile(
                "Local",
                ACCESS_KEY, SECRET_KEY,
                false,
                10000,
                "", "", "", "", true)));
    }

    @Test
    public void testS3CopyArtifact() throws Throwable {
        final String endpoint = minioServiceEndpoint;
        rr.then(r -> {
            r.createOnlineSlave(Label.get("work"));
            r.createOnlineSlave(Label.get("copy"));

            createProfile();
            createAndRunPublisher(r);

            FreeStyleProject job = r.createFreeStyleProject("copierJob");
            job.setAssignedLabel(Label.get("copy"));
            job.getBuildersList().add(new S3CopyArtifact(
                    "publisherJob",
                    new LastCompletedBuildSelector(),
                    "*.txt",
                    "",
                    "",
                    false,
                    false
            ));
            job.getBuildersList().add(new VerifyFileBuilder());
            r.buildAndAssertSuccess(job);
        });
    }

    public static class VerifyFileBuilder extends TestBuilder {
        @Override
        public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
            final FilePath child = build.getWorkspace().child("test.txt");
            assertTrue("No test.txt in workspace!", child.exists());

            final String s = child.readToString();
            assertEquals(FILE_CONTENT, s);

            return true;
        }
    }
}
