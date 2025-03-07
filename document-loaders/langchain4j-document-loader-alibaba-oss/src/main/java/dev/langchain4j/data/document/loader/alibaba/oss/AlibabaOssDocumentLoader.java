package dev.langchain4j.data.document.loader.alibaba.oss;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.alibaba.oss.AlibabaOssSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlibabaOssDocumentLoader {
    private static final Logger log = LoggerFactory.getLogger(AlibabaOssDocumentLoader.class);

    /**
     * The alibaba cloud OSS client object
     */
    private final OSS ossClient;

    public AlibabaOssDocumentLoader(final OSS ossClient) {
        this.ossClient = ensureNotNull(ossClient, "ossClient");
    }

    /**
     * Loads a single document from the specified OSS bucket based on the specified object key.
     *
     * @param bucket OSS bucket to load from.
     * @param key    The key of the OSS object which should be loaded.
     * @param parser The parser to be used for parsing text from the object.
     * @return A document containing the content of the OSS object.
     */
    public Document loadDocument(String bucket, String key, DocumentParser parser) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
        final OSSObject ossObject = ossClient.getObject(getObjectRequest);
        AlibabaOssSource source = new AlibabaOssSource(ossObject.getObjectContent(), bucket, key);
        log.info("Load document by bucket {} key {}", bucket, key);
        return DocumentLoader.load(source, parser);
    }

    /**
     * Loads all documents from an OSS bucket.
     * Skips any documents that fail to load.
     *
     * @param bucket The alibaba cloud OSS bucket name
     * @param parser DocumentParser
     * @return loaded document list
     */
    public List<Document> loadDocuments(String bucket, DocumentParser parser) {
        return loadDocuments(bucket, null, parser);
    }

    /**
     * Loads all documents from an OSS bucket with prefix.
     * Skips any documents that fail to load.
     *
     * @param bucket The alibaba cloud OSS bucket name
     * @param prefix The alibaba cloud OSS file prefix
     * @param parser DocumentParser
     * @return loaded document list
     */
    public List<Document> loadDocuments(String bucket, String prefix, DocumentParser parser) {
        final ListObjectsV2Result objects =
                Objects.nonNull(prefix) ? ossClient.listObjectsV2(bucket, prefix) : ossClient.listObjectsV2(bucket);

        final List<OSSObjectSummary> objectSummaries = objects.getObjectSummaries().stream()
                .filter(ossObjectSummary -> ossObjectSummary.getSize() > 0)
                .toList();

        final List<CompletableFuture<Document>> futures = createFutures(bucket, parser, objectSummaries);
        return collectResults(futures);
    }

    /**
     * Create the list of async task
     *
     * @param objectSummaries OSS object summaries
     * @return List of async task
     */
    private List<CompletableFuture<Document>> createFutures(
            String bucket, DocumentParser parser, List<OSSObjectSummary> objectSummaries) {
        return objectSummaries.stream()
                .map(ossObjectSummary -> createFuture(bucket, parser, ossObjectSummary))
                .toList();
    }

    /**
     * Create single async task
     *
     * @param ossObjectSummary OSS object summary
     * @return Async task
     */
    private CompletableFuture<Document> createFuture(
            String bucket, DocumentParser parser, OSSObjectSummary ossObjectSummary) {
        return CompletableFuture.supplyAsync(() -> {
            OSSObject ossObject = ossClient.getObject(bucket, ossObjectSummary.getKey());
            AlibabaOssSource source =
                    new AlibabaOssSource(ossObject.getObjectContent(), bucket, ossObjectSummary.getKey());
            return DocumentLoader.load(source, parser);
        });
    }

    /**
     * Wait for all asynchronous tasks to complete and collect the results
     *
     * @param futures List of async task
     * @return List of Document
     */
    private List<Document> collectResults(List<CompletableFuture<Document>> futures) {
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return allFutures
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
                .exceptionally(this::handleException)
                .join();
    }

    /**
     * Exception handling method
     *
     * @param throwable Throwable object
     * @return Empty list
     */
    private List<Document> handleException(Throwable throwable) {
        log.error("Error loading documents: ", throwable);
        return new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        /**
         * The alibaba cloud oss endpoint
         */
        private String endpoint;
        /**
         * The alibaba cloud oss region
         */
        private String region;
        /**
         * The alibaba cloud auth info
         */
        private AlibabaOssCredentials alibabaOssCredentials;

        /**
         * Set the alibaba cloud oss endpoint.
         *
         * @param endpoint The alibaba endpoint.
         * @return The builder instance.
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Set the alibaba cloud oss region.
         *
         * @param region The alibaba region.
         * @return The builder instance.
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Set the alibaba cloud oss region.
         *
         * @param alibabaOssCredentials The AlibabaOssCredentials.
         * @return The builder instance.
         */
        public Builder alibabaOssCredentials(AlibabaOssCredentials alibabaOssCredentials) {
            this.alibabaOssCredentials = alibabaOssCredentials;
            return this;
        }

        /**
         * Build OSS Client and set to document loader with OSS Client
         *
         * @return The alibaba oss document loader
         */
        public AlibabaOssDocumentLoader build() {
            final OSS ossClient = OSSClientBuilder.create()
                    .endpoint(endpoint)
                    .credentialsProvider(alibabaOssCredentials.toCredentialsProvider())
                    .region(region)
                    .build();
            return new AlibabaOssDocumentLoader(ossClient);
        }
    }
}
