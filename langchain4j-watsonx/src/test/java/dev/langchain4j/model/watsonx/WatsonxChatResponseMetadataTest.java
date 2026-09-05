package dev.langchain4j.model.watsonx;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.watsonx.ai.chat.TextChatResponse.DetectionEntry;
import com.ibm.watsonx.ai.chat.TextChatResponse.DetectionResult;
import com.ibm.watsonx.ai.chat.TextChatResponse.ModerationResult;
import com.ibm.watsonx.ai.chat.TextChatResponse.ModerationResult.Position;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WatsonxChatResponseMetadataTest {

    private static final Map<String, List<ModerationResult>> MODERATIONS =
            Map.of("hate", List.of(new ModerationResult(0.9f, true, new Position(0, 4), "hate", "word")));
    private static final Map<String, List<DetectionEntry>> DETECTIONS = Map.of(
            "pii",
            List.of(new DetectionEntry(
                    0, List.of(new DetectionResult("det-1", "pii", "email", 0.95, "test@test.com", 0, 13)))));

    private static WatsonxChatResponseMetadata.Builder fullyPopulated() {
        return WatsonxChatResponseMetadata.builder()
                .id("chat-id")
                .modelName("ibm/granite-3-8b-instruct")
                .tokenUsage(new TokenUsage(1, 2, 3))
                .finishReason(FinishReason.STOP)
                .created(1754000000L)
                .modelVersion("2.0.0")
                .serviceTier("premium")
                .systemFingerprint("fp-1")
                .cached(true)
                .moderations(MODERATIONS)
                .detections(DETECTIONS);
    }

    @Test
    void toBuilder_should_preserve_type_and_watsonx_fields() {
        ChatResponseMetadata rebuilt = fullyPopulated().build().toBuilder().build();

        assertThat(rebuilt).isInstanceOf(WatsonxChatResponseMetadata.class);

        WatsonxChatResponseMetadata watsonxMetadata = (WatsonxChatResponseMetadata) rebuilt;
        assertThat(watsonxMetadata.created()).isEqualTo(1754000000L);
        assertThat(watsonxMetadata.modelVersion()).isEqualTo("2.0.0");
        assertThat(watsonxMetadata.serviceTier()).isEqualTo("premium");
        assertThat(watsonxMetadata.systemFingerprint()).isEqualTo("fp-1");
        assertThat(watsonxMetadata.cached()).isTrue();
        assertThat(watsonxMetadata.moderations()).isEqualTo(MODERATIONS);
        assertThat(watsonxMetadata.detections()).isEqualTo(DETECTIONS);
    }

    @Test
    void toBuilder_should_preserve_inherited_fields() {
        ChatResponseMetadata rebuilt = fullyPopulated().build().toBuilder().build();

        assertThat(rebuilt.id()).isEqualTo("chat-id");
        assertThat(rebuilt.modelName()).isEqualTo("ibm/granite-3-8b-instruct");
        assertThat(rebuilt.tokenUsage()).isEqualTo(new TokenUsage(1, 2, 3));
        assertThat(rebuilt.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void toBuilder_should_keep_type_when_the_service_layer_replaces_token_usage() {
        ChatResponseMetadata rebuilt = fullyPopulated().build().toBuilder()
                .tokenUsage(new TokenUsage(10, 20, 30))
                .build();

        assertThat(rebuilt).isInstanceOf(WatsonxChatResponseMetadata.class);
        assertThat(rebuilt.tokenUsage()).isEqualTo(new TokenUsage(10, 20, 30));
        assertThat(((WatsonxChatResponseMetadata) rebuilt).modelVersion()).isEqualTo("2.0.0");
    }

    @Test
    void toBuilder_should_preserve_a_metadata_with_only_base_fields() {
        ChatResponseMetadata rebuilt =
                WatsonxChatResponseMetadata.builder()
                        .id("chat-id")
                        .created(1754000000L)
                        .modelVersion("2.0.0")
                        .build()
                        .toBuilder()
                        .build();

        assertThat(rebuilt).isInstanceOf(WatsonxChatResponseMetadata.class);

        WatsonxChatResponseMetadata watsonxMetadata = (WatsonxChatResponseMetadata) rebuilt;
        assertThat(watsonxMetadata.created()).isEqualTo(1754000000L);
        assertThat(watsonxMetadata.modelVersion()).isEqualTo("2.0.0");
        assertThat(watsonxMetadata.serviceTier()).isNull();
        assertThat(watsonxMetadata.systemFingerprint()).isNull();
        assertThat(watsonxMetadata.cached()).isNull();
        assertThat(watsonxMetadata.moderations()).isNull();
        assertThat(watsonxMetadata.detections()).isNull();
    }

    @Test
    void equals_and_hashCode_should_handle_null_watsonx_fields() {
        ChatResponseMetadata metadata1 =
                WatsonxChatResponseMetadata.builder().id("chat-id").build();
        ChatResponseMetadata metadata2 =
                WatsonxChatResponseMetadata.builder().id("chat-id").build();

        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1).hasSameHashCodeAs(metadata2);
        assertThat(metadata1).isNotEqualTo(fullyPopulated().build());
    }

    @Test
    void equals_and_hashCode_should_include_watsonx_fields() {
        ChatResponseMetadata metadata1 = fullyPopulated().build();
        ChatResponseMetadata metadata2 = fullyPopulated().build();

        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1).hasSameHashCodeAs(metadata2);
    }

    @Test
    void equals_should_distinguish_each_watsonx_field() {
        ChatResponseMetadata base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(fullyPopulated().created(1L).build());
        assertThat(base).isNotEqualTo(fullyPopulated().modelVersion("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().serviceTier("other").build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().systemFingerprint("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().cached(false).build());
        assertThat(base).isNotEqualTo(fullyPopulated().moderations(Map.of()).build());
        assertThat(base).isNotEqualTo(fullyPopulated().detections(Map.of()).build());
    }

    @Test
    void equals_should_still_honor_inherited_fields() {
        ChatResponseMetadata base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(fullyPopulated().id("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().modelName("other").build());
    }

    @Test
    void equals_should_reject_null_and_other_types() {
        ChatResponseMetadata base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("not a metadata object");
    }

    @Test
    void toString_should_include_watsonx_fields() {
        String text = fullyPopulated().build().toString();

        assertThat(text)
                .contains("WatsonxChatResponseMetadata{")
                .contains("created=1754000000")
                .contains("modelVersion='2.0.0'")
                .contains("serviceTier='premium'")
                .contains("systemFingerprint='fp-1'")
                .contains("cached=true")
                .contains("moderations=")
                .contains("detections=");
    }
}
