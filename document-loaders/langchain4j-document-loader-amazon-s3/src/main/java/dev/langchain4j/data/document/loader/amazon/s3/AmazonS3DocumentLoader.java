package dev.langchain4j.data.document.loader.amazon.s3;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.amazon.s3.AmazonS3Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;
import static software.amazon.awssdk.regions.Region.US_EAST_1;

public class AmazonS3DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(AmazonS3DocumentLoader.class);

    private final S3Client s3Client;

    public AmazonS3DocumentLoader(S3Client s3Client) {
        this.s3Client = ensureNotNull(s3Client, "s3Client");
    }

    /**
     * Loads a single document from the specified S3 bucket based on the specified object key.
     *
     * @param bucket S3 bucket to load from.
     * @param key    The key of the S3 object which should be loaded.
     * @param parser The parser to be used for parsing text from the object.
     * @return A document containing the content of the S3 object.
     * @throws RuntimeException If {@link S3Exception} occurs.
     */
    public Document loadDocument(String bucket, String key, DocumentParser parser) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(ensureNotBlank(bucket, "bucket"))
                    .key(ensureNotBlank(key, "key"))
                    .build();
            ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getObjectRequest);
            AmazonS3Source source = new AmazonS3Source(inputStream, bucket, key);
            return DocumentLoader.load(source, parser);
        } catch (S3Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads all documents from an S3 bucket.
     * Skips any documents that fail to load.
     *
     * @param bucket S3 bucket to load from.
     * @param parser The parser to be used for parsing text from the object.
     * @return A list of documents.
     * @throws RuntimeException If {@link S3Exception} occurs.
     */
    public List<Document> loadDocuments(String bucket, DocumentParser parser) {
        return loadDocuments(bucket, null, parser);
    }

    /**
     * Loads all documents from an S3 bucket.
     * Skips any documents that fail to load.
     *
     * @param bucket S3 bucket to load from.
     * @param prefix Only keys with the specified prefix will be loaded.
     * @param parser The parser to be used for parsing text from the object.
     * @return A list of documents.
     * @throws RuntimeException If {@link S3Exception} occurs.
     */
    public List<Document> loadDocuments(String bucket, String prefix, DocumentParser parser) {
        List<Document> documents = new ArrayList<>();

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(ensureNotBlank(bucket, "bucket"))
                .prefix(prefix)
                .build();

        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);

        List<S3Object> filteredS3Objects = listObjectsV2Response.contents().stream()
                .filter(s3Object -> !s3Object.key().endsWith("/") && s3Object.size() > 0)
                .collect(toList());

        for (S3Object s3Object : filteredS3Objects) {
            String key = s3Object.key();
            try {
                Document document = loadDocument(bucket, key, parser);
                documents.add(document);
            } catch (Exception e) {
                log.warn("Failed to load an object with key '{}' from bucket '{}', skipping it.", key, bucket, e);
            }
        }

        return documents;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Region region = US_EAST_1;
        private String endpointUrl;
        private String profile;
        private boolean forcePathStyle;
        private AwsCredentials awsCredentials;

        /**
         * Set the AWS region. Defaults to US_EAST_1
         *
         * @param region The AWS region.
         * @return The builder instance.
         */
        public Builder region(String region) {
            this.region = Region.of(region);
            return this;
        }

        /**
         * Set the AWS region. Defaults to US_EAST_1
         *
         * @param region The AWS region.
         * @return The builder instance.
         */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /**
         * Specifies a custom endpoint URL to override the default service URL.
         *
         * @param endpointUrl The endpoint URL.
         * @return The builder instance.
         */
        public Builder endpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
            return this;
        }

        /**
         * Set the profile defined in AWS credentials. If not set, it will use the default profile.
         *
         * @param profile The profile defined in AWS credentials.
         * @return The builder instance.
         */
        public Builder profile(String profile) {
            this.profile = profile;
            return this;
        }

        /**
         * Set the forcePathStyle. When enabled, it will use the path-style URL
         *
         * @param forcePathStyle The forcePathStyle.
         * @return The builder instance.
         */
        public Builder forcePathStyle(boolean forcePathStyle) {
            this.forcePathStyle = forcePathStyle;
            return this;
        }

        /**
         * Set the AWS credentials. If not set, it will use the default credentials.
         *
         * @param awsCredentials The AWS credentials.
         * @return The builder instance.
         */
        public Builder awsCredentials(AwsCredentials awsCredentials) {
            this.awsCredentials = awsCredentials;
            return this;
        }

        public AmazonS3DocumentLoader build() {
            AwsCredentialsProvider credentialsProvider = createCredentialsProvider();
            S3Client s3Client = createS3Client(credentialsProvider);
            return new AmazonS3DocumentLoader(s3Client);
        }

        private AwsCredentialsProvider createCredentialsProvider() {
            if (!isNullOrBlank(profile)) {
                return ProfileCredentialsProvider.create(profile);
            }

            if (awsCredentials != null) {
                return awsCredentials.toCredentialsProvider();
            }

            return DefaultCredentialsProvider.create();
        }

        private S3Client createS3Client(AwsCredentialsProvider credentialsProvider) {

            S3ClientBuilder s3ClientBuilder = S3Client.builder()
                    .region(region)
                    .forcePathStyle(forcePathStyle)
                    .credentialsProvider(credentialsProvider);

            if (!isNullOrBlank(endpointUrl)) {
                try {
                    s3ClientBuilder.endpointOverride(new URI(endpointUrl));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            return s3ClientBuilder.build();
        }
    }
}
