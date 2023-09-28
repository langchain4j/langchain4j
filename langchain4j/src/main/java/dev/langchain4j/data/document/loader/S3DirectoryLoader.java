package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentType;
import dev.langchain4j.data.document.UnsupportedDocumentTypeException;
import dev.langchain4j.data.document.source.S3Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.data.document.loader.DocumentLoaderUtils.parserFor;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * S3 Directory Loader Implementation
 */
public class S3DirectoryLoader extends AbstractS3Loader<List<Document>> {

    private static final Logger log = LoggerFactory.getLogger(S3DirectoryLoader.class);

    private final String prefix;

    private S3DirectoryLoader(Builder builder) {
        super(builder);

        if (isNullOrBlank(bucket)) {
            throw new IllegalArgumentException("Bucket is a required parameter.");
        }

        this.prefix = builder.prefix;
    }


    /**
     * Loads a list of documents from an S3 bucket, ignoring unsupported document types.
     * If a prefix is specified, only objects with that prefix will be loaded.
     *
     * @param s3Client The S3 client used for the operation
     * @return A list of Document objects containing the content and metadata of the S3 objects
     * @throws RuntimeException if an S3 exception occurs during the operation
     */
    @Override
    protected List<Document> load(S3Client s3Client) {
        List<Document> documents = new ArrayList<>();

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
        List<S3Object> filteredS3Objects = listObjectsV2Response.contents().stream()
                .filter(s3Object -> !s3Object.key().endsWith("/") && s3Object.size() > 0)
                .collect(Collectors.toList());

        for (S3Object s3Object : filteredS3Objects) {
            String key = s3Object.key();

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(getObjectRequest);

            try {
                documents.add(DocumentLoaderUtils.load(new S3Source(bucket, key, inputStream), parserFor(DocumentType.of(key))));
            } catch (UnsupportedDocumentTypeException e) {
                log.warn("Ignored unsupported document type", e);
            }
        }

        return documents;
    }

    public static Builder builder(String bucketName, String prefix) {
        return new Builder(bucketName, prefix);
    }

    public static Builder builder(String bucketName) {
        return new Builder(bucketName);
    }

    public static final class Builder extends AbstractS3Loader.Builder<Builder> {
        private final String prefix;

        /**
         * Set the bucket.
         *
         * @param bucket Bucket.
         */
        public Builder(String bucket) {
            super(bucket);
            this.prefix = "";
        }

        /**
         * Set the bucket and prefix.
         *
         * @param bucket Bucket.
         * @param prefix Prefix.
         */
        public Builder(String bucket, String prefix) {
            super(bucket);
            this.prefix = prefix;
        }

        @Override
        public S3DirectoryLoader build() {
            return new S3DirectoryLoader(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}

