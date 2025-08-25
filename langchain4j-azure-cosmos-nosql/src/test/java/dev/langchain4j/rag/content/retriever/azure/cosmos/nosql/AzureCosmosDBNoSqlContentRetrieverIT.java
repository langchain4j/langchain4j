package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.cosmos.implementation.guava25.collect.ImmutableList;
import com.azure.cosmos.models.CosmosFullTextIndex;
import com.azure.cosmos.models.CosmosFullTextPath;
import com.azure.cosmos.models.CosmosFullTextPolicy;
import com.azure.cosmos.models.CosmosVectorDataType;
import com.azure.cosmos.models.CosmosVectorDistanceFunction;
import com.azure.cosmos.models.CosmosVectorEmbedding;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.CosmosVectorIndexType;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
public class AzureCosmosDBNoSqlContentRetrieverIT {

    private static final Logger log = LoggerFactory.getLogger(AzureCosmosDBNoSqlContentRetrieverIT.class);

    private static final String DATABASE_NAME = "test_database_langchain_java";
    private static final String VECTOR_CONTAINER = "test_container_vector";
    private static final String TEXT_SEARCH_CONTAINER = "test_container_text_search";
    private static final String TEXT_RANK_CONTAINER = "test_container_text_rank";
    private static final String HYBRID_CONTAINER = "test_container_hybrid";

    private final EmbeddingModel embeddingModel;
    private final AzureCosmosDBNoSqlContentRetriever contentRetrieverWithVector;
    private final AzureCosmosDBNoSqlContentRetriever contentRetrieverWithFullTextSearch;
    private final AzureCosmosDBNoSqlContentRetriever contentRetrieverWithFullTextRank;
    private final AzureCosmosDBNoSqlContentRetriever contentRetrieverWithHybrid;

    public AzureCosmosDBNoSqlContentRetrieverIT() {
        embeddingModel = AzureOpenAiEmbeddingModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("text-embedding-3-large")
                .logRequestsAndResponses(false)
                .build();

        contentRetrieverWithVector = createContentRetriever(
                AzureCosmosDBSearchQueryType.VECTOR, VECTOR_CONTAINER, embeddingModel.dimension());
        contentRetrieverWithFullTextSearch = createFullTextSearchContentRetriever();
        contentRetrieverWithFullTextRank =
                createContentRetriever(AzureCosmosDBSearchQueryType.FULL_TEXT_RANKING, TEXT_RANK_CONTAINER, 0);
        contentRetrieverWithHybrid = createContentRetriever(
                AzureCosmosDBSearchQueryType.HYBRID, HYBRID_CONTAINER, embeddingModel.dimension());
    }

    private AzureCosmosDBNoSqlContentRetriever createContentRetriever(
            AzureCosmosDBSearchQueryType queryType, String containerName, Integer dimensions) {
        IndexingPolicy indexingPolicy = getIndexingPolicy(queryType);
        CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy = new CosmosVectorEmbeddingPolicy();
        CosmosFullTextPolicy cosmosFullTextPolicy = new CosmosFullTextPolicy();

        return new AzureCosmosDBNoSqlContentRetriever(
                System.getenv("AZURE_COSMOS_HOST"),
                new AzureKeyCredential(System.getenv("AZURE_COSMOS_MASTER_KEY")),
                null, // tokenCredential
                embeddingModel,
                DATABASE_NAME,
                containerName,
                "/id",
                indexingPolicy,
                cosmosVectorEmbeddingPolicy,
                cosmosFullTextPolicy,
                null,
                queryType,
                3,
                0.0,
                null);
    }

    private AzureCosmosDBNoSqlContentRetriever createFullTextSearchContentRetriever() {
        IndexingPolicy indexingPolicy = getIndexingPolicy(AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH);
        CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy = new CosmosVectorEmbeddingPolicy();
        CosmosFullTextPolicy cosmosFullTextPolicy = new CosmosFullTextPolicy();
        return new AzureCosmosDBNoSqlContentRetriever(
                System.getenv("AZURE_COSMOS_HOST"),
                new AzureKeyCredential(System.getenv("AZURE_COSMOS_MASTER_KEY")),
                null,
                null, // no embedding model for full-text
                DATABASE_NAME,
                TEXT_SEARCH_CONTAINER,
                "/id",
                indexingPolicy,
                cosmosVectorEmbeddingPolicy,
                cosmosFullTextPolicy,
                null,
                AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH,
                3,
                0.0,
                new FullTextContains("text", "bicycle"));
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_COSMOS_DB");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
        contentRetrieverWithVector.deleteContainer();
        contentRetrieverWithFullTextSearch.deleteContainer();
        contentRetrieverWithFullTextRank.deleteContainer();
        contentRetrieverWithHybrid.deleteContainer();
    }

    @Test
    void addEmbeddingsAndRetrieveRelevant() {
        String content1 = "banana";
        String content2 = "computer";
        String content3 = "apple";
        String content4 = "pizza";
        String content5 = "strawberry";

        TextSegment segment1 = TextSegment.from(content1, Metadata.from("category", "fruit"));
        TextSegment segment2 = TextSegment.from(content2, Metadata.from("category", "electronics"));
        TextSegment segment3 = TextSegment.from(content3, Metadata.from("category", "fruit"));
        TextSegment segment4 = TextSegment.from(content4, Metadata.from("category", "food"));
        TextSegment segment5 = TextSegment.from(content5, Metadata.from("category", "fruit"));

        Embedding embedding1 =
                new Embedding(embeddingModel.embed(content1).content().vector());
        Embedding embedding2 =
                new Embedding(embeddingModel.embed(content2).content().vector());
        Embedding embedding3 =
                new Embedding(embeddingModel.embed(content3).content().vector());
        Embedding embedding4 =
                new Embedding(embeddingModel.embed(content4).content().vector());
        Embedding embedding5 =
                new Embedding(embeddingModel.embed(content5).content().vector());

        contentRetrieverWithVector.add(embedding1, segment1);
        contentRetrieverWithVector.add(embedding2, segment2);
        contentRetrieverWithVector.add(embedding3, segment3);
        contentRetrieverWithVector.add(embedding4, segment4);
        contentRetrieverWithVector.add(embedding5, segment5);

        List<Content> relevant = contentRetrieverWithVector.retrieve(Query.from("fruit"));
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).textSegment().text()).isIn(content1, content3, content5);
        assertThat(relevant.get(1).textSegment().text()).isIn(content1, content3, content5);
        assertThat(relevant.get(2).textSegment().text()).isIn(content1, content3, content5);
    }

    @Test
    void addEmbeddingsAndRetrieveRelevantWithFullTextSearch() {
        String content1 = "red bicycle for sale";
        String content2 = "blue skateboard available";
        String content3 = "green bicycle in stock";
        String content4 = "yellow car for rent";

        TextSegment segment1 = TextSegment.from(content1, Metadata.from("type", "sports"));
        TextSegment segment2 = TextSegment.from(content2, Metadata.from("type", "sports"));
        TextSegment segment3 = TextSegment.from(content3, Metadata.from("type", "sports"));
        TextSegment segment4 = TextSegment.from(content4, Metadata.from("type", "vehicle"));

        contentRetrieverWithFullTextSearch.add(segment1);
        contentRetrieverWithFullTextSearch.add(segment2);
        contentRetrieverWithFullTextSearch.add(segment3);
        contentRetrieverWithFullTextSearch.add(segment4);

        List<Content> relevant = contentRetrieverWithFullTextSearch.retrieve(Query.from("bicycle"));
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).textSegment().text()).containsIgnoringCase("bicycle");
        assertThat(relevant.get(1).textSegment().text()).containsIgnoringCase("bicycle");
    }

    @Test
    void addEmbeddingsAndRetrieveRelevantWithFullTextRank() {
        String content1 = "red bicycle for sale";
        String content2 = "blue skateboard available";
        String content3 = "green bicycle in stock";
        String content4 = "yellow car for rent";

        TextSegment segment1 = TextSegment.from(content1, Metadata.from("type", "sports"));
        TextSegment segment2 = TextSegment.from(content2, Metadata.from("type", "sports"));
        TextSegment segment3 = TextSegment.from(content3, Metadata.from("type", "sports"));
        TextSegment segment4 = TextSegment.from(content4, Metadata.from("type", "vehicle"));

        contentRetrieverWithFullTextRank.add(segment1);
        contentRetrieverWithFullTextRank.add(segment2);
        contentRetrieverWithFullTextRank.add(segment3);
        contentRetrieverWithFullTextRank.add(segment4);

        List<Content> relevant = contentRetrieverWithFullTextRank.retrieve(Query.from("bicycle"));
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).textSegment().text()).containsIgnoringCase("bicycle");
        assertThat(relevant.get(1).textSegment().text()).containsIgnoringCase("bicycle");
        assertThat(relevant.get(2).textSegment().text()).containsIgnoringCase("skateboard");
    }

    @Test
    void addEmbeddingsAndRetrieveRelevantWithHybridSearch() {
        String content1 = "premium red bicycle for enthusiasts";
        String content2 = "basic blue skateboard for beginners";
        String content3 = "professional green bicycle for racing";

        TextSegment segment1 = TextSegment.from(content1, Metadata.from("category", "premium"));
        TextSegment segment2 = TextSegment.from(content2, Metadata.from("category", "basic"));
        TextSegment segment3 = TextSegment.from(content3, Metadata.from("category", "professional"));

        Embedding embedding1 =
                new Embedding(embeddingModel.embed(content1).content().vector());
        Embedding embedding2 =
                new Embedding(embeddingModel.embed(content2).content().vector());
        Embedding embedding3 =
                new Embedding(embeddingModel.embed(content3).content().vector());

        contentRetrieverWithHybrid.add(embedding1, segment1);
        contentRetrieverWithHybrid.add(embedding2, segment2);
        contentRetrieverWithHybrid.add(embedding3, segment3);

        List<Content> relevant = contentRetrieverWithHybrid.retrieve(Query.from("bicycle racing"));
        assertThat(relevant).isNotEmpty();
        // Should find bicycle-related content with hybrid search
        assertThat(relevant.get(0).textSegment().text()).containsIgnoringCase("bicycle");
    }

    private IndexingPolicy getIndexingPolicy(AzureCosmosDBSearchQueryType searchQueryType) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        ExcludedPath excludedPath = new ExcludedPath("/*");
        indexingPolicy.setExcludedPaths(singletonList(excludedPath));
        IncludedPath includedPath1 = new IncludedPath("/metadata/?");
        IncludedPath includedPath2 = new IncludedPath("/content/?");
        indexingPolicy.setIncludedPaths(ImmutableList.of(includedPath1, includedPath2));

        if (searchQueryType.equals(AzureCosmosDBSearchQueryType.VECTOR)
                || searchQueryType.equals(AzureCosmosDBSearchQueryType.HYBRID)) {
            CosmosVectorIndexSpec cosmosVectorIndexSpec = new CosmosVectorIndexSpec();
            cosmosVectorIndexSpec.setPath("/embedding");
            cosmosVectorIndexSpec.setType(CosmosVectorIndexType.DISK_ANN.toString());
            indexingPolicy.setVectorIndexes(List.of(cosmosVectorIndexSpec));
        }

        if (searchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH)
                || searchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_RANKING)) {
            CosmosFullTextIndex cosmosFullTextIndex = new CosmosFullTextIndex();
            cosmosFullTextIndex.setPath("/text");
            indexingPolicy.setCosmosFullTextIndexes(List.of(cosmosFullTextIndex));
        }

        return indexingPolicy;
    }

    private CosmosVectorEmbeddingPolicy getCosmosVectorEmbeddingPolicy() {
        // Set vector embedding policy
        CosmosVectorEmbeddingPolicy embeddingPolicy = new CosmosVectorEmbeddingPolicy();
        CosmosVectorEmbedding embedding = new CosmosVectorEmbedding();
        embedding.setPath("/embedding");
        embedding.setDataType(CosmosVectorDataType.FLOAT32);
        embedding.setEmbeddingDimensions(1536);
        embedding.setDistanceFunction(CosmosVectorDistanceFunction.COSINE);
        embeddingPolicy.setCosmosVectorEmbeddings(singletonList(embedding));
        return embeddingPolicy;
    }

    private CosmosFullTextPolicy getCosmosFullTextPolicy() {
        // Set full text policy
        CosmosFullTextPolicy fullTextPolicy = new CosmosFullTextPolicy();
        CosmosFullTextPath fullTextPath = new CosmosFullTextPath();
        fullTextPath.setPath("/text");
        fullTextPath.setLanguage("en-US");
        fullTextPolicy.setPaths(singletonList(fullTextPath));
        fullTextPolicy.setDefaultLanguage("en-US");
        return fullTextPolicy;
    }
}
