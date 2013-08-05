package hudson.plugins.s3;

public final class Region {

    public static final Region[] REGIONS = {
            new Region("US Standard", "s3.amazonaws.com", ""),
            new Region("US West (Oregon)", "s3-us-west-2.amazonaws.com", "us-west-2"),
            new Region("US West (Northern California)", "s3-us-west-1.amazonaws.com", "us-west-1"),
            new Region("EU (Ireland)", "s3-eu-west-1.amazonaws.com", "eu-west-1"),
            new Region("Asia Pacific (Singapore)", "s3-ap-southeast-1.amazonaws.com", "ap-southeast-1"),
            new Region("Asia Pacific (Tokyo)", "s3-ap-northeast-1.amazonaws.com", "ap-northeast-1"),
            new Region("South America (Sao Paulo)", "s3-sa-east-1.amazonaws.com", "sa-east-1"),
    };

    public final String name;
    public final String endpoint;
    public final String locationConstraint;

    public Region(String name, String endpoint, String locationConstraint) {
        this.name = name;
        this.endpoint = endpoint;
        this.locationConstraint = locationConstraint;
    }

    @Override
    public String toString() {
        return "Region{" +
                "name='" + name + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", locationConstraint='" + locationConstraint + '\'' +
                '}';
    }

}
