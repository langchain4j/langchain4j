package dev.langchain4j.data.document.loader.tencent.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.tencent.cos.TencentCosSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

public class TencentCosDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(TencentCosDocumentLoader.class);

    private final COSClient cosClient;

    public TencentCosDocumentLoader(COSClient s3Client) {
        this.cosClient = ensureNotNull(s3Client, "cosClient");
    }

    /**
     * Loads a single document from the specified COS bucket based on the specified object key.
     *
     * @param bucket COS bucket to load from.
     * @param key    The key of the COS object which should be loaded.
     * @param parser The parser to be used for parsing text from the object.
     * @return A document containing the content of the COS object.
     */
    public Document loadDocument(String bucket, String key, DocumentParser parser) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
        COSObject cosObject = cosClient.getObject(getObjectRequest);
        TencentCosSource source = new TencentCosSource(cosObject.getObjectContent(), bucket, key);

        return DocumentLoader.load(source, parser);
    }

    /**
     * Loads all documents from an COS bucket.
     * Skips any documents that fail to load.
     *
     * @param bucket COS bucket to load from.
     * @param parser The parser to be used for parsing text from the object.
     * @return A list of documents.
     */
    public List<Document> loadDocuments(String bucket, DocumentParser parser) {
        return loadDocuments(bucket, null, parser);
    }

    /**
     * Loads all documents from an COS bucket.
     * Skips any documents that fail to load.
     *
     * @param bucket COS bucket to load from.
     * @param prefix Only keys with the specified prefix will be loaded.
     * @param parser The parser to be used for parsing text from the object.
     * @return A list of documents.
     */
    public List<Document> loadDocuments(String bucket, String prefix, DocumentParser parser) {
        List<Document> documents = new ArrayList<>();

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(ensureNotBlank(bucket, "bucket"))
                .withPrefix(prefix);

        ObjectListing objectListing = cosClient.listObjects(listObjectsRequest);

        List<COSObjectSummary> filteredObjects = objectListing.getObjectSummaries().stream()
                .filter(object -> !object.getKey().endsWith("/") && object.getSize() > 0)
                .collect(toList());

        for (COSObjectSummary object : filteredObjects) {
            String key = object.getKey();
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

        private Region region;
        private TencentCredentials tencentCredentials;

        /**
         * Set the Tencent region.
         *
         * @param region The Tencent region.
         * @return The builder instance.
         */
        public Builder region(String region) {
            this.region = new Region(Region.formatRegion(region));
            return this;
        }

        /**
         * Set the Tencent region.
         *
         * @param region The Tencent region.
         * @return The builder instance.
         */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /**
         * Set the Tencent credentials. If not set, it will use the default credentials.
         *
         * @param tencentCredentials The Tencent credentials.
         * @return The builder instance.
         */
        public Builder tencentCredentials(TencentCredentials tencentCredentials) {
            this.tencentCredentials = tencentCredentials;
            return this;
        }

        public TencentCosDocumentLoader build() {
            COSCredentialsProvider credentialsProvider = createCredentialsProvider();
            COSClient cosClient = createCosClient(credentialsProvider);
            return new TencentCosDocumentLoader(cosClient);
        }

        private COSCredentialsProvider createCredentialsProvider() {
            if (tencentCredentials != null) {
                return tencentCredentials.toCredentialsProvider();
            }

            throw new IllegalArgumentException("Tencent credentials are required.");
        }

        private COSClient createCosClient(COSCredentialsProvider cosCredentialsProvider) {
            ClientConfig clientConfig = new ClientConfig(region);
            return new COSClient(cosCredentialsProvider, clientConfig);
        }

    }
}
