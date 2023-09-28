package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentType;
import dev.langchain4j.data.document.source.S3Source;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;


import static dev.langchain4j.data.document.loader.DocumentLoaderUtils.parserFor;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * S3 File Loader Implementation
 */
public class S3FileLoader extends AbstractS3Loader<Document> {
    private final String key;

    private S3FileLoader(Builder builder) {
        super(builder);

        if (isNullOrBlank(bucket) || isNullOrBlank(builder.key)) {
            throw new IllegalArgumentException("Bucket and key are required parameters.");
        }

        this.key = builder.key;
    }

    /**
     * Loads a document from an S3 bucket based on the specified key.
     *
     * @param s3Client The S3 client used for the operation
     * @return A Document object containing the content and metadata of the S3 object
     * @throws RuntimeException if an S3 exception occurs during the operation
     */
    @Override
    protected Document load(S3Client s3Client) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
            ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(objectRequest);
            return DocumentLoaderUtils.load(new S3Source(bucket, key, inputStream), parserFor(DocumentType.of(key)));
        } catch (S3Exception e) {
            throw new RuntimeException("Encountered an error from s3", e);
        }
    }

    public static Builder builder(String bucketName, String fileName) {
        return new Builder(bucketName, fileName);
    }

    public static final class Builder extends AbstractS3Loader.Builder<Builder> {
        private final String key;

        /**
         * Set the bucket and object key.
         *
         * @param bucket Bucket.
         * @param key Key.
         */
        public Builder(String bucket, String key) {
            super(bucket);
            this.key = key;
        }

        @Override
        public S3FileLoader build() {
            return new S3FileLoader(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
