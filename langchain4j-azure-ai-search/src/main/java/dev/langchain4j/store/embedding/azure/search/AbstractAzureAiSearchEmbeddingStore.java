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
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public abstract class AbstractAzureAiSearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(AbstractAzureAiSearchEmbeddingStore.class);

    static final String INDEX_NAME = "vectorsearch";

    static final String DEFAULT_FIELD_ID = "id";

    protected static final String DEFAULT_FIELD_CONTENT = "content";

    protected final String DEFAULT_FIELD_CONTENT_VECTOR = "content_vector";

    protected static final String DEFAULT_FIELD_METADATA = "metadata";

    protected static final String DEFAULT_FIELD_METADATA_SOURCE = "source";

    protected static final String DEFAULT_FIELD_METADATA_ATTRS = "attributes";

    protected static final String SEMANTIC_SEARCH_CONFIG_NAME = "semantic-search-config";

    protected static final String VECTOR_ALGORITHM_NAME = "vector-search-algorithm";

    protected static final String VECTOR_SEARCH_PROFILE_NAME = "vector-search-profile";

    private SearchIndexClient searchIndexClient;

    protected SearchClient searchClient;

    protected void initialize(String endpoint, AzureKeyCredential keyCredential, TokenCredential tokenCredential, int dimensions, SearchIndex index) {
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
     *
     * @param dimensions The number of dimensions of the embeddings.
     */
    public void createOrUpdateIndex(int dimensions) {
        ensureTrue(dimensions > 0, "Dimensions must be greater than 0");

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
                .setVectorSearchProfileName(VECTOR_SEARCH_PROFILE_NAME));
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
                        new HnswAlgorithmConfiguration(VECTOR_ALGORITHM_NAME)
                                .setParameters(
                                        new HnswParameters()
                                                .setMetric(VectorSearchAlgorithmMetric.COSINE)
                                                .setM(4)
                                                .setEfSearch(500)
                                                .setEfConstruction(400))))
                .setProfiles(Collections.singletonList(
                        new VectorSearchProfile(VECTOR_SEARCH_PROFILE_NAME, VECTOR_ALGORITHM_NAME)));

        SemanticSearch semanticSearch = new SemanticSearch().setDefaultConfigurationName(SEMANTIC_SEARCH_CONFIG_NAME)
                .setConfigurations(singletonList(
                        new SemanticConfiguration(SEMANTIC_SEARCH_CONFIG_NAME,
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
     *
     * @param index The index to be created or updated.
     */
    void createOrUpdateIndex(SearchIndex index) {
        searchIndexClient.createOrUpdateIndex(index);
    }

    public void deleteIndex() {
        searchIndexClient.deleteIndex(INDEX_NAME);
    }

    /**
     * Add an embedding to the store.
     */
    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    /**
     * Add an embedding to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Add an embedding and the related content to the store.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Add a list of embeddings to the store.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    /**
     * Add a list of embeddings, and the list of related content, to the store.
     */
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

        List<Document> documents = new ArrayList<>();
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
            documents.add(document);
        }
        List<IndexingResult> indexingResults = searchClient.uploadDocuments(documents).getResults();
        for (IndexingResult indexingResult : indexingResults) {
            if (!indexingResult.isSucceeded()) {
                throw new AzureAiSearchRuntimeException("Failed to add embedding: " + indexingResult.getErrorMessage());
            } else {
                log.debug("Added embedding: {}", indexingResult.getKey());
            }
        }
    }

    float[] doublesListToFloatArray(List<Double> doubles) {
        float[] array = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); ++i) {
            array[i] = doubles.get(i).floatValue();
        }
        return array;
    }

    /**
     * Calculates LangChain4j's RelevanceScore from Azure AI Search's score.
     * <p>
     * Score in Azure AI Search is transformed into a cosine similarity as described here:
     * https://learn.microsoft.com/en-us/azure/search/vector-search-ranking#scores-in-a-vector-search-results
     * <p>
     * RelevanceScore in LangChain4j is a derivative of cosine similarity,
     * but it compresses it into 0..1 range (instead of -1..1) for ease of use.
     */
    protected static double fromAzureScoreToRelevanceScore(double score) {
        double cosineDistance = (1 - score) / score;
        double cosineSimilarity = -cosineDistance + 1;
        return RelevanceScore.fromCosineSimilarity(cosineSimilarity);
    }
}
