package dev.langchain4j.data.document;

import dev.langchain4j.data.document.source.S3Source;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;


import static dev.langchain4j.data.document.DocumentLoaderUtils.parserFor;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * S3 File Loader Implementation
 */
public class S3FileLoader extends AbstractS3Loader<Document> {
    private final String key;

    private S3FileLoader(Builder builder) {
        super(builder);
        this.key = ensureNotBlank(builder.key, "key");
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
            throw new RuntimeException("Failed to load document from s3", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractS3Loader.Builder<Builder> {
        private String key;

        /**
         * Set the object key.
         *
         * @param key Key.
         */
        public Builder key(String key) {
            this.key = key;
            return this;
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
