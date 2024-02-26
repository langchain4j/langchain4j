package dev.langchain4j.store.embedding.astradb;

import com.dtsx.astra.sdk.AstraDB;
import com.dtsx.astra.sdk.AstraDBAdmin;
import com.dtsx.astra.sdk.AstraDBCollection;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModelName;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.stargate.sdk.data.domain.SimilarityMetric;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.UUID;

import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("AstraDB is not available in the CI")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk.*")
@Slf4j
class AstraDbEmbeddingStoreIT extends EmbeddingStoreIT {

    static final String TEST_DB = "test_langchain4j";
    static final String TEST_COLLECTION = "test_collection";
    static AstraDbEmbeddingStore embeddingStore;
    static EmbeddingModel embeddingModel;

    static UUID dbId;
    static AstraDB db;

    @BeforeAll
    public static void initStoreForTests() {
        AstraDBAdmin astraDBAdminClient = new AstraDBAdmin(getAstraToken());
        dbId = astraDBAdminClient.createDatabase(TEST_DB);
        assertNotNull(dbId);
        log.info("[init] - Database exists id={}", dbId);

        // Select the Database as working object
        db = astraDBAdminClient.database(dbId);
        assertNotNull(db);

        AstraDBCollection collection =
                db.createCollection(TEST_COLLECTION, 1536, SimilarityMetric.cosine);
        log.info("[init] - Collection create name={}", TEST_COLLECTION);

        // Creating the store (and collection) if not exists
        embeddingStore = new AstraDbEmbeddingStore(collection);
        log.info("[init] - Embedding Store initialized");
    }

    @Override
    protected void clearStore() {
        embeddingStore.clear();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        if (embeddingModel == null) {
            embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(OpenAiModelName.TEXT_EMBEDDING_ADA_002)
                    .build();
        }
        return embeddingModel;
    }

    void testAddEmbeddingAndFindRelevant() {

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.from("Text", Metadata.from("Key", "Value"));
        String id = embeddingStore.add(embedding, textSegment);
        assertTrue(id != null && !id.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = embeddingStore.findRelevant(refereceEmbedding, 1);
        assertEquals(1, embeddingMatches.size());

        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatches.get(0);
        assertThat(embeddingMatch.score()).isBetween(0d, 1d);
        assertThat(embeddingMatch.embeddingId()).isEqualTo(id);
        assertThat(embeddingMatch.embedding()).isEqualTo(embedding);
        assertThat(embeddingMatch.embedded()).isEqualTo(textSegment);
    }



}
