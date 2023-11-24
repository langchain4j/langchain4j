package dev.langchain4j.store.embedding.vertexai.internal;


import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.storage.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Builder
public class GcpBlobService {

    @NonNull
    private final String project;
    @NonNull
    private final String bucketName;
    private final CredentialsProvider credentialsProvider;

    @Getter(lazy = true)
    private final Bucket bucket = initBucket();
    @Getter(lazy = true)
    private final Storage storage = initStorage();

    /**
     * Lists all the blobs in the bucket.
     *
     * @return the stream of blob names
     */
    public Stream<String> list() {
        return getBucket()
                .list()
                .streamValues()
                .map(BlobInfo::getName);
    }

    /**
     * Deletes all the blobs.
     *
     * @param names the blob names
     */
    public void deleteAll(List<String> names) {
        getBucket().get(names).forEach(Blob::delete);
    }

    /**
     * Deletes the blob.
     *
     * @param blobName the blob name
     */
    public void delete(String blobName) {
        final Blob blob = getBucket().get(blobName);
        if (blob != null && blob.exists()) {
            blob.delete();
        }
    }

    /**
     * Checks if the blob exists.
     *
     * @param blobName the blob name
     * @return true if the blob exists
     */
    public boolean exists(String blobName) {
        final Blob blob = getBucket().get(blobName);
        return blob != null && blob.exists();
    }

    /**
     * Downloads the blob.
     *
     * @param blobName the blob name
     * @return the blob content
     */
    public String download(String blobName) {
        final Blob blob = getBucket().get(blobName);
        if (blob == null || !blob.exists()) {
            return null;
        }

        return new String(blob.getContent());
    }

    /**
     * Upload a vertex ai document.
     *
     * @param blobName the blob name
     * @param document the document
     */
    public void upload(String blobName, VertexAIDocument document) {
        upload(blobName, document.toJson());
    }

    /**
     * Uploads the blob.
     *
     * @param blobName the blob name
     * @param data     the blob content
     */
    public void upload(String blobName, String data) {
        upload(blobName, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Uploads the blob.
     *
     * @param blobName the blob name
     * @param data     the blob content
     */
    public void upload(String blobName, byte[] data) {
        getBucket().create(blobName, data);
    }

    /**
     * Initializes the bucket.
     *
     * @return the bucket
     */
    private Bucket initBucket() {
        return getStorage().get(bucketName);
    }

    /**
     * Initializes the storage.
     *
     * @return the storage
     */
    private Storage initStorage() {
        if (credentialsProvider != null) {
            try {
                return StorageOptions
                        .newBuilder()
                        .setCredentials(credentialsProvider.getCredentials())
                        .setProjectId(project)
                        .build()
                        .getService();
            } catch (IOException e) {
                log.error("Failed to create storage client.", e);
                throw new RuntimeException(e);
            }
        }

        return StorageOptions
                .newBuilder()
                .setProjectId(project)
                .build()
                .getService();
    }


}
