package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.azure.search.AzureAiSearchFilterMapper;
import dev.langchain4j.store.embedding.EmbeddingStore;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

/**
 * Azure AI Search EmbeddingStore Implementation
 */
public class AzureAiSearchEmbeddingStore extends AbstractAzureAiSearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, boolean createOrUpdateIndex, int dimensions, String indexName, AzureAiSearchFilterMapper filterMapper) {
        this.initialize(endpoint, keyCredential, null, createOrUpdateIndex, dimensions, null, indexName, filterMapper);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, boolean createOrUpdateIndex, SearchIndex index, String indexName, AzureAiSearchFilterMapper filterMapper) {
        this.initialize(endpoint, keyCredential, null, createOrUpdateIndex, 0, index, indexName, filterMapper);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, boolean createOrUpdateIndex, int dimensions, String indexName, AzureAiSearchFilterMapper filterMapper) {
        this.initialize(endpoint, null, tokenCredential, createOrUpdateIndex, dimensions, null, indexName, filterMapper);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, boolean createOrUpdateIndex, SearchIndex index, String indexName, AzureAiSearchFilterMapper filterMapper) {
        this.initialize(endpoint, null, tokenCredential, createOrUpdateIndex, 0, index, indexName, filterMapper);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;

        private AzureKeyCredential keyCredential;

        private TokenCredential tokenCredential;

        private boolean createOrUpdateIndex = true;

        private int dimensions;

        private SearchIndex index;

        private String indexName;

        private AzureAiSearchFilterMapper filterMapper;

        /**
         * Sets the Azure AI Search endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure AI Search endpoint in the format: https://{resource}.search.windows.net
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure AI Search API key.
         *
         * @param apiKey The Azure AI Search API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.keyCredential = new AzureKeyCredential(apiKey);
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * Whether to create or update the search index.
         *
         * @param createOrUpdateIndex Whether to create or update the index.
         * @return builder
         */
        public Builder createOrUpdateIndex(boolean createOrUpdateIndex) {
            this.createOrUpdateIndex = createOrUpdateIndex;
            return this;
        }

        /**
         * If using the ready-made index, sets the number of dimensions of the embeddings.
         * This parameter is exclusive of the index parameter.
         *
         * @param dimensions The number of dimensions of the embeddings.
         * @return builder
         */
        public Builder dimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * If using a custom index, sets the index to be used.
         * This parameter is exclusive of the dimensions parameter.
         *
         * @param index The index to be used.
         * @return builder
         */
        public Builder index(SearchIndex index) {
            this.index = index;
            return this;
        }

        /**
         * If no index is provided, set the name of the default index to be used.
         *
         * @param indexName The name of the index to be used.
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * Sets the filter mapper to be used.
         *
         * @param filterMapper The filter mapper to be used.
         * @return builder
         */
        public Builder filterMapper(AzureAiSearchFilterMapper filterMapper) {
            this.filterMapper = filterMapper;
            return this;
        }

        public AzureAiSearchEmbeddingStore build() {
            ensureNotNull(endpoint, "endpoint");
            ensureTrue(keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");
            ensureTrue(dimensions > 0 || index != null, "either dimensions or index must be set");
            if (keyCredential == null) {
                if (index == null) {
                    return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential, createOrUpdateIndex, dimensions, indexName, filterMapper);
                } else {
                    return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential, createOrUpdateIndex, index, indexName, filterMapper);
                }
            } else {
                if (index == null) {
                    return new AzureAiSearchEmbeddingStore(endpoint, keyCredential, createOrUpdateIndex, dimensions, indexName, filterMapper);
                } else {
                    return new AzureAiSearchEmbeddingStore(endpoint, keyCredential, createOrUpdateIndex, index, indexName, filterMapper);
                }
            }
        }
    }
}
