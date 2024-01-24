package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.*;
import com.azure.search.documents.models.*;
import com.azure.search.documents.util.SearchPagedIterable;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Azure AI Search EmbeddingStore Implementation
 */
public class AzureAiSearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchEmbeddingStore.class);

    private static final String INDEX_NAME = "vectorsearch";

    private static final String DEFAULT_FIELD_ID = "id";

    private static final String DEFAULT_FIELD_CONTENT = "content";

    private static final String DEFAULT_FIELD_CONTENT_VECTOR = "content_vector";

    private static final String DEFAULT_FIELD_METADATA = "metadata";

    private static final String DEFAULT_FIELD_METADATA_SOURCE = "source";

    private static final String DEFAULT_FIELD_METADATA_ATTRS = "attributes";

    private final SearchIndexClient searchIndexClient;

    private final SearchClient searchClient;

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, EmbeddingModel embeddingModel) {

        searchIndexClient = new SearchIndexClientBuilder()
                .endpoint(endpoint)
                .credential(keyCredential)
                .buildClient();

        searchClient = new SearchClientBuilder()
                .endpoint(endpoint)
                .credential(keyCredential)
                .indexName(INDEX_NAME)
                .buildClient();

        createOrUpdateIndex(embeddingModel);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, EmbeddingModel embeddingModel) {

        searchIndexClient = new SearchIndexClientBuilder()
                .endpoint(endpoint)
                .credential(tokenCredential)
                .buildClient();

        searchClient = new SearchClientBuilder()
                .endpoint(endpoint)
                .credential(tokenCredential)
                .indexName(INDEX_NAME)
                .buildClient();

        createOrUpdateIndex(embeddingModel);
    }

     void createOrUpdateIndex(EmbeddingModel embeddingModel) {

        // Embed a test query to get the embedding dimensions
        int embeddingDimensions = embeddingModel.embed("test").content().vector().length;

        List<SearchField> fields = new ArrayList<>();
        fields.add(new SearchField(DEFAULT_FIELD_ID, SearchFieldDataType.STRING)
                .setKey(true)
                .setFilterable(true));
        fields.add(new SearchField(DEFAULT_FIELD_CONTENT, SearchFieldDataType.STRING)
                .setSearchable(true)
                .setFilterable(true));
        fields.add(new SearchField(DEFAULT_FIELD_CONTENT_VECTOR, SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                .setSearchable(true)
                .setVectorSearchDimensions(embeddingDimensions)
                .setVectorSearchProfileName("vector-search-profile"));
        fields.add((new SearchField(DEFAULT_FIELD_METADATA, SearchFieldDataType.COMPLEX)).setFields(
                Arrays.asList(
                        new SearchField(DEFAULT_FIELD_METADATA_SOURCE, SearchFieldDataType.STRING)
                                .setFilterable(true),
                        (new SearchField(DEFAULT_FIELD_METADATA_ATTRS, SearchFieldDataType.collection(SearchFieldDataType.COMPLEX))).setFields(
                                Arrays.asList(
                                        new SearchField("key", SearchFieldDataType.STRING)
                                                .setFilterable(true),
                                        new SearchField("value", SearchFieldDataType.STRING)
                                                .setFilterable(true)
                                )
                        )

                )
        ));

        VectorSearch vectorSearch = new VectorSearch()
                .setAlgorithms(Collections.singletonList(
                        new HnswAlgorithmConfiguration("vector-search-algorithm")
                                .setParameters(
                                        new HnswParameters()
                                                .setMetric(VectorSearchAlgorithmMetric.COSINE)
                                                .setM(4)
                                                .setEfSearch(500)
                                                .setEfConstruction(400))))
                .setProfiles(Collections.singletonList(
                        new VectorSearchProfile("vector-search-profile", "vector-search-algorithm")));

        SemanticSearch semanticSearch = new SemanticSearch().setDefaultConfigurationName("semantic-search-config")
                .setConfigurations(Arrays.asList(
                        new SemanticConfiguration("semantic-search-config",
                                new SemanticPrioritizedFields()
                                        .setContentFields(new SemanticField(DEFAULT_FIELD_CONTENT))
                                        .setKeywordsFields(new SemanticField(DEFAULT_FIELD_CONTENT)))));


        SearchIndex index = new SearchIndex(INDEX_NAME)
                .setFields(fields)
                .setVectorSearch(vectorSearch)
                .setSemanticSearch(semanticSearch);

        searchIndexClient.createOrUpdateIndex(index);
    }

    void cleanUpIndex() {
        searchIndexClient.deleteIndex(INDEX_NAME);
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id        The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding   The embedding to be added to the store.
     * @param textSegment Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    /**
     * Adds multiple embeddings and their corresponding contents that have been embedded to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded   A list of original contents that were embedded.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        List<Float> vector = floatsArrayToList(referenceEmbedding.vector());
        VectorizedQuery vectorizedQuery = new VectorizedQuery(vector)
                .setFields(DEFAULT_FIELD_CONTENT_VECTOR)
                .setKNearestNeighborsCount(maxResults);

        SearchPagedIterable searchResults = searchClient.search(null, new SearchOptions()
                        .setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorizedQuery)),
                Context.NONE);

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        for (SearchResult searchResult : searchResults) {
            Double score = searchResult.getScore();
            SearchDocument searchDocument = searchResult.getDocument(SearchDocument.class);
            String embeddingId = (String) searchDocument.get(DEFAULT_FIELD_ID);
            List<Double> embeddingList = (List<Double>) searchDocument.get(DEFAULT_FIELD_CONTENT_VECTOR);
            float[] embeddingArray = doublesListToFloatArray(embeddingList);
            Embedding embedding = Embedding.from(embeddingArray);
            String embbededContent = (String) searchDocument.get(DEFAULT_FIELD_CONTENT);
            EmbeddingMatch<TextSegment> embeddingMatch;
            if (isNotNullOrBlank(embbededContent)) {
                TextSegment embedded = TextSegment.textSegment(embbededContent);
                embeddingMatch = new EmbeddingMatch<>(score, embeddingId, embedding, embedded);
            } else {
                embeddingMatch = new EmbeddingMatch<>(score, embeddingId, embedding, null);
            }
            result.add(embeddingMatch);
        }
        return result;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(
            List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        List<SearchDocument> searchDocuments = new ArrayList<>();
        for (int i = 0; i < ids.size(); ++i) {
            SearchDocument searchDocument = new SearchDocument();
            searchDocument.put(DEFAULT_FIELD_ID, ids.get(i));
            searchDocument.put(DEFAULT_FIELD_CONTENT_VECTOR, floatsArrayToList(embeddings.get(i).vector()));
            if (embedded != null) {
                searchDocument.put(DEFAULT_FIELD_CONTENT, embedded.get(i).text());
                TextSegment embeddedSegment = embedded.get(i);
                searchDocument.put(DEFAULT_FIELD_METADATA, new HashMap<String, Object>() {{
                    put(DEFAULT_FIELD_METADATA_SOURCE, "langchain4j");
                    put(DEFAULT_FIELD_METADATA_ATTRS, new HashMap<String, Object>() {{
                        if (embeddedSegment != null) {
                            this.putAll(embeddedSegment.metadata().asMap());
                        }
                    }});
                }});
            } else {
                searchDocument.put(DEFAULT_FIELD_CONTENT, "");
            }
            searchDocuments.add(searchDocument);
        }
        List<IndexingResult> indexingResults = searchClient.uploadDocuments(searchDocuments).getResults();
        for (IndexingResult indexingResult : indexingResults) {
            if (!indexingResult.isSucceeded()) {
                log.error("Failed to add embedding: {}", indexingResult.getErrorMessage());
            } else {
                log.info("Added embedding: {}", indexingResult.getKey());
            }
        }
    }

    private List<Float> floatsArrayToList(float[] floats) {
        List<Float> list = new ArrayList<>();
        for (float f : floats) {
            list.add(f);
        }
        return list;
    }

    private float[] doublesListToFloatArray(List<Double> doubles) {
        float[] array = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); ++i) {
            array[i] = doubles.get(i).floatValue();
        }
        return array;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;

        private AzureKeyCredential keyCredential;

        private TokenCredential tokenCredential;

        private EmbeddingModel embeddingModel;

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
         * Sets the embedding model.
         *
         * @param embeddingModel The embedding model.
         * @return builder
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public AzureAiSearchEmbeddingStore build() {
            ensureNotNull(endpoint, "endpoint");
            ensureNotNull(embeddingModel, "embeddingModel");
            ensureTrue(keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");
            if (keyCredential != null) {
                return new AzureAiSearchEmbeddingStore(endpoint, keyCredential, embeddingModel);
            } else {
                return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential, embeddingModel);
            }
        }
    }
}
