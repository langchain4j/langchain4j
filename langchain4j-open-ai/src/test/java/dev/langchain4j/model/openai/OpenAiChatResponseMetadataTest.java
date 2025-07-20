package dev.langchain4j.model.openai;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatResponseMetadataTest {

    @Test
    public void should_modify_specific_properties_via_builder() {
        // given
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();
        OpenAiChatResponseMetadata original = OpenAiChatResponseMetadata.builder()
                .id("test-id")
                .modelName("gpt-4")
                .tokenUsage(tokenUsage)
                .finishReason(STOP)
                .created(1234567890L)
                .serviceTier("tier1")
                .systemFingerprint("fingerprint123")
                .build();

        // when
        OpenAiChatResponseMetadata modified = original.toBuilder()
                .modelName("gpt-3.5-turbo")
                .created(9876543210L)
                .build();

        // then
        assertThat(modified.id()).isEqualTo("test-id");
        assertThat(modified.modelName()).isEqualTo("gpt-3.5-turbo");
        assertThat(modified.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(modified.finishReason()).isEqualTo(STOP);
        assertThat(modified.created()).isEqualTo(9876543210L);
        assertThat(modified.serviceTier()).isEqualTo("tier1");
        assertThat(modified.systemFingerprint()).isEqualTo("fingerprint123");

        // verify original remains unchanged
        assertThat(original.modelName()).isEqualTo("gpt-4");
        assertThat(original.created()).isEqualTo(1234567890L);
    }

    @Test
    public void should_modify_parent_properties_via_builder() {
        // given
        OpenAiTokenUsage originalTokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();
        OpenAiTokenUsage newTokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(40)
                .outputTokenCount(50)
                .totalTokenCount(90)
                .build();

        OpenAiChatResponseMetadata original = OpenAiChatResponseMetadata.builder()
                .id("original-id")
                .modelName("davinci")
                .tokenUsage(originalTokenUsage)
                .finishReason(STOP)
                .created(1234567890L)
                .serviceTier("basic")
                .systemFingerprint("fp-abc")
                .build();

        // when
        OpenAiChatResponseMetadata modified = original.toBuilder()
                .id("modified-id")
                .tokenUsage(newTokenUsage)
                .finishReason(LENGTH)
                .build();

        // then
        assertThat(modified.id()).isEqualTo("modified-id");
        assertThat(modified.modelName()).isEqualTo("davinci");
        assertThat(modified.tokenUsage()).isEqualTo(newTokenUsage);
        assertThat(modified.finishReason()).isEqualTo(LENGTH);
        assertThat(modified.created()).isEqualTo(1234567890L);
        assertThat(modified.serviceTier()).isEqualTo("basic");
        assertThat(modified.systemFingerprint()).isEqualTo("fp-abc");

        // verify original remains unchanged
        assertThat(original.id()).isEqualTo("original-id");
        assertThat(original.tokenUsage()).isEqualTo(originalTokenUsage);
        assertThat(original.finishReason()).isEqualTo(STOP);
    }

    @Test
    public void should_modify_all_properties_via_builder() {
        // given
        OpenAiTokenUsage originalTokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .totalTokenCount(3)
                .build();
        OpenAiTokenUsage newTokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(4)
                .outputTokenCount(5)
                .totalTokenCount(9)
                .build();

        OpenAiChatResponseMetadata original = OpenAiChatResponseMetadata.builder()
                .id("id-1")
                .modelName("model-1")
                .tokenUsage(originalTokenUsage)
                .finishReason(STOP)
                .created(1000L)
                .serviceTier("tier-a")
                .systemFingerprint("fp-1")
                .build();

        // when
        OpenAiChatResponseMetadata modified = original.toBuilder()
                .id("id-2")
                .modelName("model-2")
                .tokenUsage(newTokenUsage)
                .finishReason(LENGTH)
                .created(2000L)
                .serviceTier("tier-b")
                .systemFingerprint("fp-2")
                .build();

        // then
        assertThat(modified)
                .isNotEqualTo(original)
                .satisfies(metadata -> {
                    assertThat(metadata.id()).isEqualTo("id-2");
                    assertThat(metadata.modelName()).isEqualTo("model-2");
                    assertThat(metadata.tokenUsage()).isEqualTo(newTokenUsage);
                    assertThat(metadata.finishReason()).isEqualTo(LENGTH);
                    assertThat(metadata.created()).isEqualTo(2000L);
                    assertThat(metadata.serviceTier()).isEqualTo("tier-b");
                    assertThat(metadata.systemFingerprint()).isEqualTo("fp-2");
                });
    }
}
