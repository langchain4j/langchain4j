package dev.langchain4j.store.embedding.tablestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.search.FieldSchema;
import com.alicloud.openservices.tablestore.model.search.FieldType;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TABLESTORE_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_INSTANCE_NAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_SECRET", matches = ".+")
class TablestoreEmbeddingStoreExampleIT {

    @Test
    void simple() {

        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        /*
         * Step 1: create a TablestoreEmbeddingStore.
         */
        String endpoint = System.getenv("TABLESTORE_ENDPOINT");
        String instanceName = System.getenv("TABLESTORE_INSTANCE_NAME");
        String accessKeyId = System.getenv("TABLESTORE_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("TABLESTORE_ACCESS_KEY_SECRET");
        TablestoreEmbeddingStore embeddingStore = new TablestoreEmbeddingStore(
                new SyncClient(endpoint, accessKeyId, accessKeySecret, instanceName),
                384,
                Arrays.asList(
                        new FieldSchema("meta_example_keyword", FieldType.KEYWORD),
                        new FieldSchema("meta_example_long", FieldType.LONG),
                        new FieldSchema("meta_example_double", FieldType.DOUBLE),
                        new FieldSchema("meta_example_text", FieldType.TEXT)
                                .setAnalyzer(FieldSchema.Analyzer.MaxWord)));
        /*
         * Step 2: init.
         *
         * Note: It only needs to be executed once, and the first execution requires
         * waiting for table and index initialization
         */
        embeddingStore.init();

        /*
         * Step 3: Add some docs.
         */
        TextSegment segment1 = TextSegment.from(
                "I like football.",
                new Metadata()
                        .put("meta_example_keyword", "a")
                        .put("meta_example_long", 123)
                        .put("meta_example_double", 1.5)
                        .put("meta_example_text", "dog cat"));
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        embeddingStore.add(embedding1, segment1);

        TextSegment segment2 = TextSegment.from(
                "The weather is good today.",
                new Metadata()
                        .put("meta_example_keyword", "b")
                        .put("meta_example_long", 456)
                        .put("meta_example_double", 5.6)
                        .put("meta_example_text", "foo boo"));
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        embeddingStore.add(embedding2, segment2);

        /*
         * Step 4: Search
         */
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(
                        embeddingModel.embed("What is your favourite sport?").content())
                .filter(new IsLessThan("meta_example_double", 0.5))
                .maxResults(100)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        // get result detail.
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            String embeddingId = match.embeddingId();
            Double score = match.score();
            Embedding embedding = match.embedding();
            TextSegment embedded = match.embedded();
            String text = embedded.text();
            Metadata metadata = embedded.metadata();
            assertThat(embeddingId).isNotNull();
            assertThat(score).isNotNull();
            assertThat(embedding).isNotNull();
            assertThat(text).isNotNull();
            assertThat(metadata).isNotNull();
        }

        /*
         * Step 5: Delete docs.
         */
        embeddingStore.remove("id_example");
        embeddingStore.removeAll();
    }
}
