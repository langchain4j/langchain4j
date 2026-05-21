package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class GoogleGenAiChatResponseMetadataTest {

    @Test
    void should_build_with_raw_response() {
        GenerateContentResponse rawResponse = GenerateContentResponse.builder().build();

        GoogleGenAiChatResponseMetadata metadata = GoogleGenAiChatResponseMetadata.builder()
                .rawResponse(rawResponse)
                .tokenUsage(new TokenUsage(10, 5))
                .finishReason(FinishReason.STOP)
                .build();

        assertThat(metadata.rawResponse()).isSameAs(rawResponse);
        assertThat(metadata.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_build_with_all_fields() {
        GenerateContentResponse rawResponse = GenerateContentResponse.builder()
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .build())
                .build();

        GoogleGenAiChatResponseMetadata metadata = GoogleGenAiChatResponseMetadata.builder()
                .id("test-id")
                .modelName("gemini-2.0-flash")
                .tokenUsage(new TokenUsage(10, 5))
                .finishReason(FinishReason.STOP)
                .rawResponse(rawResponse)
                .build();

        assertThat(metadata.id()).isEqualTo("test-id");
        assertThat(metadata.modelName()).isEqualTo("gemini-2.0-flash");
        assertThat(metadata.rawResponse()).isSameAs(rawResponse);
    }

    @Test
    void should_build_with_null_raw_response() {
        GoogleGenAiChatResponseMetadata metadata = GoogleGenAiChatResponseMetadata.builder()
                .tokenUsage(new TokenUsage(0, 0))
                .finishReason(FinishReason.STOP)
                .build();

        assertThat(metadata.rawResponse()).isNull();
    }

    @Test
    void should_support_to_builder() {
        GenerateContentResponse rawResponse = GenerateContentResponse.builder().build();

        GoogleGenAiChatResponseMetadata metadata = GoogleGenAiChatResponseMetadata.builder()
                .id("test-id")
                .tokenUsage(new TokenUsage(10, 5))
                .finishReason(FinishReason.STOP)
                .rawResponse(rawResponse)
                .build();

        GoogleGenAiChatResponseMetadata rebuilt =
                (GoogleGenAiChatResponseMetadata) metadata.toBuilder().build();

        assertThat(rebuilt.id()).isEqualTo("test-id");
        assertThat(rebuilt.rawResponse()).isSameAs(rawResponse);
        assertThat(rebuilt.tokenUsage().inputTokenCount()).isEqualTo(10);
    }

    @Test
    void should_implement_equals_and_hashcode() {
        GenerateContentResponse rawResponse = GenerateContentResponse.builder().build();

        GoogleGenAiChatResponseMetadata metadata1 = GoogleGenAiChatResponseMetadata.builder()
                .id("test-id")
                .rawResponse(rawResponse)
                .build();

        GoogleGenAiChatResponseMetadata metadata2 = GoogleGenAiChatResponseMetadata.builder()
                .id("test-id")
                .rawResponse(rawResponse)
                .build();

        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
    }

    @Test
    void should_not_equal_different_metadata() {
        GoogleGenAiChatResponseMetadata metadata1 =
                GoogleGenAiChatResponseMetadata.builder().id("id-1").build();

        GoogleGenAiChatResponseMetadata metadata2 =
                GoogleGenAiChatResponseMetadata.builder().id("id-2").build();

        assertThat(metadata1).isNotEqualTo(metadata2);
    }

    @Test
    void should_have_toString() {
        GoogleGenAiChatResponseMetadata metadata = GoogleGenAiChatResponseMetadata.builder()
                .id("test-id")
                .modelName("gemini-2.0-flash")
                .finishReason(FinishReason.STOP)
                .build();

        String str = metadata.toString();
        assertThat(str).contains("test-id");
        assertThat(str).contains("gemini-2.0-flash");
        assertThat(str).contains("STOP");
    }
}
