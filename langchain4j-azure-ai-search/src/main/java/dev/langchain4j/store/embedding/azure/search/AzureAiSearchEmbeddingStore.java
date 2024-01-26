package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
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
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
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

    private SearchIndexClient searchIndexClient;

    private SearchClient searchClient;

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, int dimensions) {
        this.initialize(endpoint, keyCredential, null, dimensions, null);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, AzureKeyCredential keyCredential, SearchIndex index) {
        this.initialize(endpoint, keyCredential, null, 0, index);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, int dimensions) {
        this.initialize(endpoint, null, tokenCredential, dimensions, null);
    }

    public AzureAiSearchEmbeddingStore(String endpoint, TokenCredential tokenCredential, SearchIndex index) {
        this.initialize(endpoint, null, tokenCredential, 0, index);
    }

    private void initialize(String endpoint, AzureKeyCredential keyCredential, TokenCredential tokenCredential, int dimensions, SearchIndex index) {
        if (keyCredential != null) {
            searchIndexClient = new SearchIndexClientBuilder()
                    .endpoint(endpoint)
                    .credential(keyCredential)
                    .buildClient();

            searchClient = new SearchClientBuilder()
                    .endpoint(endpoint)
                    .credential(keyCredential)
                    .indexName(INDEX_NAME)
                    .buildClient();
        } else {
            searchIndexClient = new SearchIndexClientBuilder()
                    .endpoint(endpoint)
                    .credential(tokenCredential)
                    .buildClient();

            searchClient = new SearchClientBuilder()
                    .endpoint(endpoint)
                    .credential(tokenCredential)
                    .indexName(INDEX_NAME)
                    .buildClient();
        }


        if (index == null) {
            createOrUpdateIndex(dimensions);
        } else {
            createOrUpdateIndex(index);
        }
    }

    /**
     * Creates or updates the index using a ready-made index.
     * @param dimensions The number of dimensions of the embeddings.
     */
     void createOrUpdateIndex(int dimensions) {
        List<SearchField> fields = new ArrayList<>();
        fields.add(new SearchField(DEFAULT_FIELD_ID, SearchFieldDataType.STRING)
                .setKey(true)
                .setFilterable(true));
        fields.add(new SearchField(DEFAULT_FIELD_CONTENT, SearchFieldDataType.STRING)
                .setSearchable(true)
                .setFilterable(true));
        fields.add(new SearchField(DEFAULT_FIELD_CONTENT_VECTOR, SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                .setSearchable(true)
                .setVectorSearchDimensions(dimensions)
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

    /**
     * Creates or updates the index, with full control on its configuration.
     * @param index The index to be created or updated.
     */
    void createOrUpdateIndex(SearchIndex index) {
        searchIndexClient.createOrUpdateIndex(index);
    }

    public void deleteIndex() {
        searchIndexClient.deleteIndex(INDEX_NAME);
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        List<Float> vector = referenceEmbedding.vectorAsList();
        VectorizedQuery vectorizedQuery = new VectorizedQuery(vector)
                .setFields(DEFAULT_FIELD_CONTENT_VECTOR)
                .setKNearestNeighborsCount(maxResults);

        SearchPagedIterable searchResults =
                searchClient.search(null,
                        new SearchOptions()
                                .setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorizedQuery)),
                        Context.NONE);

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        for (SearchResult searchResult : searchResults) {
            Double score = fromAzureScoreToRelevanceScore(searchResult.getScore());
            if (score < minScore) {
                continue;
            }
            SearchDocument searchDocument = searchResult.getDocument(SearchDocument.class);
            String embeddingId = (String) searchDocument.get(DEFAULT_FIELD_ID);
            List<Double> embeddingList = (List<Double>) searchDocument.get(DEFAULT_FIELD_CONTENT_VECTOR);
            float[] embeddingArray = doublesListToFloatArray(embeddingList);
            Embedding embedding = Embedding.from(embeddingArray);
            String embeddedContent = (String) searchDocument.get(DEFAULT_FIELD_CONTENT);
            EmbeddingMatch<TextSegment> embeddingMatch;
            if (isNotNullOrBlank(embeddedContent)) {
                LinkedHashMap metadata = (LinkedHashMap) searchDocument.get(DEFAULT_FIELD_METADATA);
                List attributes = (List) metadata.get(DEFAULT_FIELD_METADATA_ATTRS);
                Map<String, String> attributesMap = new HashMap<>();
                for (Object attribute : attributes) {
                    LinkedHashMap innerAttribute = (LinkedHashMap) attribute;
                    String key = (String) innerAttribute.get("key");
                    String value = (String) innerAttribute.get("value");
                    attributesMap.put(key, value);
                }
                Metadata langChainMetadata = Metadata.from(attributesMap);
                TextSegment embedded = TextSegment.textSegment(embeddedContent, langChainMetadata);
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

        List<Document> searchDocuments = new ArrayList<>();
        for (int i = 0; i < ids.size(); ++i) {
            Document document = new Document();
            document.setId(ids.get(i));
            document.setContentVector(embeddings.get(i).vectorAsList());
            if (embedded != null) {
                document.setContent(embedded.get(i).text());
                Document.Metadata metadata = new Document.Metadata();
                List<Document.Metadata.Attribute> attributes = new ArrayList<>();
                for (Map.Entry<String, String> entry : embedded.get(i).metadata().asMap().entrySet()) {
                    Document.Metadata.Attribute attribute = new Document.Metadata.Attribute();
                    attribute.setKey(entry.getKey());
                    attribute.setValue(entry.getValue());
                    attributes.add(attribute);
                }
                metadata.setAttributes(attributes);
                document.setMetadata(metadata);
            }
            searchDocuments.add(document);
        }
        List<IndexingResult> indexingResults = searchClient.uploadDocuments(searchDocuments).getResults();
        for (IndexingResult indexingResult : indexingResults) {
            if (!indexingResult.isSucceeded()) {
                throw new AzureAiSearchRuntimeException("Failed to add embedding: " + indexingResult.getErrorMessage());
            } else {
                log.debug("Added embedding: {}", indexingResult.getKey());
            }
        }
    }

    private float[] doublesListToFloatArray(List<Double> doubles) {
        float[] array = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); ++i) {
            array[i] = doubles.get(i).floatValue();
        }
        return array;
    }

    /**
     * Calculates LangChain4J's RelevanceScore from Azure AI Search's score.
     *
     * Score in Azure AI Search is transformed into a cosine similarity as described here:
     * https://learn.microsoft.com/en-us/azure/search/vector-search-ranking#scores-in-a-vector-search-results
     *
     * RelevanceScore in LangChain4J is a derivative of cosine similarity,
     * but it compresses it into 0..1 range (instead of -1..1) for ease of use.
     */
    private double fromAzureScoreToRelevanceScore(double score) {
        double cosineDistance = (1 - score) / score;
        double cosineSimilarity = -cosineDistance + 1;
        return RelevanceScore.fromCosineSimilarity(cosineSimilarity);
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

        public AzureAiSearchEmbeddingStore build() {
            ensureNotNull(endpoint, "endpoint");
            ensureTrue(keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");
            ensureTrue(dimensions > 0 || index != null, "either dimensions or index must be set");
            if (keyCredential == null) {
                if (index == null) {
                    return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential, dimensions);
                } else {
                    return new AzureAiSearchEmbeddingStore(endpoint, tokenCredential, index);
                }
            } else {
                if (index == null) {
                    return new AzureAiSearchEmbeddingStore(endpoint, keyCredential, dimensions);
                } else {
                    return new AzureAiSearchEmbeddingStore(endpoint, keyCredential, index);
                }
            }
        }
    }
}
