package dev.langchain4j.data.document.loader;


import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.net.URISyntaxException;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

public abstract class AbstractS3Loader<T> {
    protected final String bucket;
    protected final String region;
    protected final String endpointUrl;
    protected final String profile;

    protected final boolean forcePathStyle;
    protected final AwsCredentials awsCredentials;

    protected AbstractS3Loader(Builder builder) {
        this.bucket = builder.bucket;
        this.region = builder.region;
        this.endpointUrl = builder.endpointUrl;
        this.profile = builder.profile;
        this.forcePathStyle = builder.forcePathStyle;
        this.awsCredentials = builder.awsCredentials;
    }

    /**
     * Initiates the loading process by configuring the AWS credentials and S3 client,
     * then loads either a document or a list of documents.
     *
     * @return A generic object of type T, which could be a Document or a list of Documents
     * @throws RuntimeException if there are issues with AWS credentials or S3 client configuration
     */
    public T load() {
        AwsCredentialsProvider awsCredentialsProvider = configureCredentialsProvider();
        S3Client s3Client = configureS3Client(awsCredentialsProvider);
        return load(s3Client);
    }

    private static AwsSessionCredentials toAwsSessionCredentials(AwsCredentials awsCredentials) {
        return AwsSessionCredentials.create(awsCredentials.getAccessKeyId(), awsCredentials.getSecretAccessKey(), awsCredentials.getSessionToken());
    }

    private static software.amazon.awssdk.auth.credentials.AwsCredentials toAwsCredentials(AwsCredentials awsCredentials) {
        return AwsBasicCredentials.create(awsCredentials.getAccessKeyId(), awsCredentials.getSecretAccessKey());
    }

    protected abstract T load(S3Client s3Client);

    private AwsCredentialsProvider configureCredentialsProvider() {
        AwsCredentialsProvider provider = DefaultCredentialsProvider.builder().build();

        if (awsCredentials != null) {
            if (awsCredentials.hasAccessKeyIdAndSecretKey()) {
                provider = StaticCredentialsProvider.create(toAwsCredentials(awsCredentials));
            } else if (awsCredentials.hasAllCredentials()) {
                provider = StaticCredentialsProvider.create(toAwsSessionCredentials(awsCredentials));
            } else {
                throw new IllegalArgumentException("Invalid AWS credentials");
            }
        }

        if( isNotNullOrBlank(profile) ) {
            provider = ProfileCredentialsProvider.create(profile);
        }

        return provider;
    }

    private S3Client configureS3Client(AwsCredentialsProvider provider) {
        S3ClientBuilder s3ClientBuilder = S3Client.builder()
                .region(Region.of(isNotNullOrBlank(region) ? region : Region.US_EAST_1.id()))
                .forcePathStyle(forcePathStyle)
                .credentialsProvider(provider);

        if (!isNullOrBlank(endpointUrl)) {
            try {
                s3ClientBuilder.endpointOverride(new URI(endpointUrl));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid URL: " + endpointUrl, e);
            }
        }

        return s3ClientBuilder.build();
    }

    public static abstract class Builder<T extends Builder<T>> {
        private final String bucket;
        private String region;
        private String endpointUrl;
        private String profile;

        private boolean forcePathStyle;
        private AwsCredentials awsCredentials;

        public Builder(String bucket) {
            this.bucket = bucket;
        }

        /**
         * Set the AWS region. Defaults to US_EAST_1
         *
         * @param region The AWS region.
         * @return The builder instance.
         */
        public T region(String region) {
            this.region = region;
            return self();
        }

        /**
         * Specifies a custom endpoint URL to override the default service URL.
         *
         * @param endpointUrl The endpoint URL.
         * @return The builder instance.
         */
        public T endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return self();
        }

        /**
         * Set the profile defined in AWS credentials. If not set, it will use the default profile.
         *
         * @param profile The profile defined in AWS credentials.
         * @return The builder instance.
         */
        public T profile(String profile) {
            this.profile = profile;
            return self();
        }

        /**
         * Set the forcePathStyle. When enabled, it will use the path-style URL
         *
         * @param forcePathStyle The forcePathStyle.
         * @return The builder instance.
         */
        public T forcePathStyle(boolean forcePathStyle) {
            this.forcePathStyle = forcePathStyle;
            return self();
        }

        /**
         * Set the AWS credentials. If not set, it will use the default credentials.
         *
         * @param awsCredentials The AWS credentials.
         * @return The builder instance.
         */
        public T awsCredentials(AwsCredentials awsCredentials) {
            this.awsCredentials = awsCredentials;
            return self();
        }

        public abstract AbstractS3Loader build();

        protected abstract T self();
    }
}

