package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class VertexAiMatchingEngineIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testMatchingEngine() throws IOException {

        EmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project("[PROJECT_ID]")
                .location("[LOCATION]")
                .publisher("google")
                .modelName("textembedding-gecko@001")
                .maxRetries(3)
                .build();

        final Embedding embedding = embeddingModel.embed("lunch").content();

        VertexAiMatchingEngine matchingEngine = VertexAiMatchingEngine
                .builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .bucketName("[BUCKET_NAME]")
                .indexId("[INDEX_ID]")
                .deployedIndexId("[DEPLOYED_INDEX_ID]")
                .indexEndpointId("[INDEX_ENDPOINT_ID]")
                .location("[LOCATION]")
                .project("[PROJECT_ID]")
                .build();

        assertThat(matchingEngine.isAvoidDups()).isTrue();

        final List<EmbeddingMatch<TextSegment>> matches = matchingEngine.findRelevant(embedding, 2, 0.5);
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).score()).isGreaterThan(0.5);
        assertThat(matches.get(1).score()).isGreaterThan(0.5);
    }
}
