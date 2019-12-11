package hudson.plugins.s3;

import hudson.FilePath;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MD5 {
    public static String generateFromFile(File file) throws IOException {
        try(InputStream inputStream = new FileInputStream(file.getAbsolutePath())) {
            return getMD5FromStream(inputStream);
        }
    }

    public static String generateBase64FromFile(FilePath file) throws IOException, InterruptedException {
        try(InputStream inputStream = file.read()) {
            return getBase64MD5FromStream(inputStream);
        }
    }

    public static String generateFromFile(FilePath file) throws IOException, InterruptedException {
        try(InputStream inputStream = file.read()) {
            return getMD5FromStream(inputStream);
        }
    }

    private static String getMD5FromStream(InputStream stream) throws IOException {
        return DigestUtils.md5Hex(stream);
    }

    private static String getBase64MD5FromStream(InputStream stream) throws IOException {
        return new String(Base64.encodeBase64(DigestUtils.md5(stream)));
    }
}
