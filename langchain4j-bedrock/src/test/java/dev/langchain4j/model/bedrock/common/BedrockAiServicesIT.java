package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Model.COHERE_EMBED_ENGLISH_V3;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Truncate.END;
import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_LITE;
import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_PRO;
import static dev.langchain4j.model.bedrock.TestedModels.CLAUDE_3_HAIKU;
import static dev.langchain4j.model.bedrock.TestedModels.COHERE_COMMAND_R_PLUS;
import static dev.langchain4j.model.bedrock.TestedModels.MISTRAL_LARGE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockAiServicesIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                AWS_NOVA_MICRO, AWS_NOVA_LITE, AWS_NOVA_PRO, COHERE_COMMAND_R_PLUS, MISTRAL_LARGE, CLAUDE_3_HAIKU);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
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

    public static void sleepIfNeeded() {
        sleepIfNeeded(1);
    }

    public static void sleepIfNeeded(int multiplier) {
        try {
            String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_BEDROCK");
            if (ciDelaySeconds != null) {
                Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L * multiplier);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
