package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Model.COHERE_EMBED_ENGLISH_V3;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Model.COHERE_EMBED_MULTILINGUAL_V3;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Truncate.END;
import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockCohereEmbeddingModelIT {

    @Test
    void cohereMultilingualEmbeddingModel() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_ENGLISH_V3)
                .inputType(SEARCH_QUERY)
                .build();

        assertThat(embeddingModel).isNotNull();

        Response<Embedding> response = embeddingModel.embed(TextSegment.from("How are you?"));
        assertThat(response).isNotNull();

        Embedding embedding = response.content();

        assertThat(embedding.vector()).hasSize(1024);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void cohereMultilingualEmbeddingModelBatch() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .truncate(END)
                .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = List.of(TextSegment.from("How are you?"), TextSegment.from("What is your name?"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void should_use_default_size_for_large_batch() {
        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_ENGLISH_V3)
                .inputType(SEARCH_QUERY)
                .truncate(END)
                .build();

        assertThat(embeddingModel).isNotNull();

        int numberOfSegments = 165;
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("This is dummy text segment " + (i + 1)));
        }

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();

        // Verify all segments were processed
        assertThat(embeddings).hasSize(numberOfSegments);

        // Verify each embedding has the correct dimension
        for (Embedding embedding : embeddings) {
            assertThat(embedding.vector()).hasSize(1024);
        }

        // Verify embeddings are not null or empty
        assertThat(embeddings).allMatch(embedding -> embedding.vector() != null && embedding.vector().length > 0);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void should_use_custom_batch_size_for_large_batch() {
        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel.builder()
                .model(COHERE_EMBED_ENGLISH_V3)
                .inputType(SEARCH_QUERY)
                .truncate(END)
                .maxSegmentsPerBatch(45)
                .build();

        assertThat(embeddingModel).isNotNull();

        int numberOfSegments = 102;
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("This is dummy text segment " + (i + 1)));
        }

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();

        assertThat(embeddings).hasSize(numberOfSegments);

        // Verify embeddings exists for all textSegments
        assertThat(embeddings).allMatch(embedding -> embedding.vector() != null && embedding.vector().length > 0);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
