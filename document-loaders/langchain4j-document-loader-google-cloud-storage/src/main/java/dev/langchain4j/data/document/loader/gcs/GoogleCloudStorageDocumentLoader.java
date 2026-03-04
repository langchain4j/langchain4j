package dev.langchain4j.data.document.loader.gcs;

import com.google.auth.Credentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.api.gax.paging.Page;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.gcs.GcsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Google Cloud Storage Document Loader to load documents from Google Cloud Storage buckets.
 */
public class GoogleCloudStorageDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudStorageDocumentLoader.class);

    private final Storage storage;

    private GoogleCloudStorageDocumentLoader(String project, Credentials credentials) {
        StorageOptions.Builder storageBuilder = StorageOptions.newBuilder();

        if (project != null) {
            storageBuilder.setProjectId(ensureNotBlank(project, "project"));
        }

        if (credentials != null) {
            storageBuilder.setCredentials(credentials);
        }

        this.storage = storageBuilder.build().getService();
    }

    /**
     * Loads a single document from the specified Google Cloud Storage bucket based on the specified object key.
     *
     * @param bucket   GCS bucket to load from.
     * @param objectName The key of the GCS object which should be loaded.
     * @param parser   The parser to be used for parsing text from the object.
     * @return A document containing the content of the GCS object.
     */
    public Document loadDocument(String bucket, String objectName, DocumentParser parser) {
        Blob blob = storage.get(bucket, objectName);
        if (blob == null) {
            throw new IllegalArgumentException("Object gs://" + bucket + "/" + objectName + " couldn't be found.");
        }

        GcsSource gcsSource = new GcsSource(blob);
        return DocumentLoader.load(gcsSource, ensureNotNull(parser, "parser"));
    }

    /**
     * Load a list of documents from the specified bucket, filtered with a glob pattern.
     * Skips any documents that fail to load.
     *
     * @param bucket the bucket to load files from
     * @param globPattern filter only files matching the glob pattern, see https://cloud.google.com/storage/docs/json_api/v1/objects/list#list-object-glob
     * @param parser the parser to use to parse the document
     * @return A list of documents from the bucket that match the glob pattern.
     */
    public List<Document> loadDocuments(String bucket, String globPattern, DocumentParser parser) {
        Page<Blob> blobs = globPattern != null ?
            storage.list(bucket, Storage.BlobListOption.currentDirectory(), Storage.BlobListOption.matchGlob(globPattern)) :
            storage.list(bucket, Storage.BlobListOption.currentDirectory());

        List<Document> documents = new ArrayList<>();

        for (Blob blob : blobs.iterateAll()) {
            try {
                GcsSource gcsSource = new GcsSource(blob);
                documents.add(DocumentLoader.load(gcsSource, ensureNotNull(parser, "parser")));
            } catch (Exception e) {
                log.warn("Failed to load blob '{}' from bucket '{}', skipping it.", blob.getName(), bucket, e);
            }
        }

        return documents;
    }

    /**
     * Loads all documents from an GCS bucket.
     *
     * @param bucket the bucket to load from.
     * @param parser The parser to be used for parsing text from the object.
     * @return A list of documents.
     */
    public List<Document> loadDocuments(String bucket, DocumentParser parser) {
        return loadDocuments(bucket, null, parser);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String project;
        private Credentials credentials;

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder credentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public GoogleCloudStorageDocumentLoader build() {
            return new GoogleCloudStorageDocumentLoader(project, credentials);
        }
    }
}
