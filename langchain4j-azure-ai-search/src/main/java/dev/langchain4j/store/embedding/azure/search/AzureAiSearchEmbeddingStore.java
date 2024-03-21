package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

/**
 * Azure AI Search EmbeddingStore Implementation
 */
public class AzureAiSearchEmbeddingStore extends AbstractAzureAiSearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, int dimensions) {
        this.initialize(endpoint, keyCredential, null, dimensions, null, true);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, SearchIndex index) {
        this.initialize(endpoint, keyCredential, null, 0, index, true);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, int dimensions) {
        this.initialize(endpoint, null, tokenCredential, dimensions, null, true);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, SearchIndex index) {
        this.initialize(endpoint, null, tokenCredential, 0, index, true);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential) {
        this.initialize(endpoint, null, tokenCredential, 0, null, false);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential) {
        this.initialize(endpoint, keyCredential, null, 0, null, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;

        private AzureKeyCredential keyCredential;

        private TokenCredential tokenCredential;

        private int dimensions;

        private SearchIndex index;

        private boolean createOrUpdateIndex;

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
         * build the store with
         *
         * @param createOrUpdateIndex
         * @return
         */
        public Builder createOrUpdateIndex(boolean createOrUpdateIndex) {
            this.createOrUpdateIndex = createOrUpdateIndex;
            return this;
        }

        public AzureAiSearchEmbeddingStore build() {
            ensureNotNull(endpoint, "endpoint");
            ensureTrue(keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");
            if (createOrUpdateIndex) {
                ensureTrue(dimensions > 0 || index != null, "either dimensions or index must be set");
                if (keyCredential == null) {
                    if (index == null) {
                        return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential, dimensions);
                    } else {
                        return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential);
                    }
                } else {
                    if (index == null) {
                        return new AzureAiSearchEmbeddingStore(endpoint, keyCredential);
                    } else {
                        return new AzureAiSearchEmbeddingStore(endpoint, keyCredential);
                    }
                }
            } else {
                if (keyCredential == null) {
                    return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential);
                } else {
                    return new AzureAiSearchEmbeddingStore(endpoint, keyCredential);
                }
            }
        }
    }
}
