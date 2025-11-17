package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.connection.ClusterDescription;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.EMBEDDING_MODEL;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.createDefaultClient;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.data.Percentage.withPercentage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MongoDbEmbeddingStoreMiscIT {

    MongoDbTestFixture fixture;

    MongoClient createClient() {
        return createDefaultClient();
    }

    protected EmbeddingStore<TextSegment> embeddingStore() {
        return fixture.getEmbeddingStore();
    }

    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @AfterEach
    void afterEach() {
        if (fixture != null) {
            fixture.afterTests();
        }
    }

    @Test
    void should_append_langchain_metadata_to_cluster() {
        // given
        final MongoClient client = createClient();

        // when
        fixture = new MongoDbTestFixture(client)
                // initialization of EmbeddingStore happens here
                .initialize(builder -> builder.filter(Filters.and(Filters.eq("metadata.test-key", "test-value"))));

        // then
        assertThat(fixture.getEmbeddingStore()).isNotNull();
        final BsonDocument clientMetadata = ((MongoClientImpl) client).getCluster().getClientMetadata().getBsonDocument();
        assertThat(clientMetadata).withFailMessage("ClientMetadata must be present in MongoClient").isNotNull();

        final String allDriverNames = clientMetadata.getDocument("driver").getString("name").getValue();
        assertThat(allDriverNames).withFailMessage(String.format("driver name %s must contain langchain4j", allDriverNames)).contains("langchain4j");


    }
    @Test
    void should_find_relevant_with_native_filter() {
        // given
        fixture = new MongoDbTestFixture(createClient())
                .initialize(builder -> builder.filter(Filters.and(Filters.eq("metadata.test-key", "test-value"))));

        TextSegment segment = TextSegment.from("this segment should be found", Metadata.from("test-key", "test-value"));
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        TextSegment filterSegment =
                TextSegment.from("this segment should not be found", Metadata.from("test-key", "no-value"));
        Embedding filterEmbedding = embeddingModel().embed(filterSegment.text()).content();

        List<String> ids = embeddingStore().addAll(asList(embedding, filterEmbedding), asList(segment, filterSegment));
        assertThat(ids).hasSize(2);

        TextSegment refSegment = TextSegment.from("find a segment");
        Embedding refEmbedding = embeddingModel().embed(refSegment.text()).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(refEmbedding)
                .maxResults(2)
                .build();

        awaitUntilAsserted(() -> {
            // when
            List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().search(searchRequest).matches();

            // then
            assertThat(relevant).hasSize(1);

            EmbeddingMatch<TextSegment> match = relevant.get(0);
            assertThat(match.score()).isCloseTo(0.88, withPercentage(1));
            assertThat(match.embeddingId()).isEqualTo(ids.get(0));
            assertThat(match.embedding()).isEqualTo(embedding);
            assertThat(match.embedded()).isEqualTo(segment);
        });
    }

    @Test
    void should_fail_when_index_absent() {
        fixture = new MongoDbTestFixture(createClient());
        try {
            fixture = fixture.initialize(builder -> builder.createIndex(false));
            fail("Expected exception");
        } catch (RuntimeException r) {
            assertThat(r.getMessage()).contains("Search Index 'test_index' not found");
        }
    }

}
