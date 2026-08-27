package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class AnthropicChatResponseMetadataTest {

    @Test
    void should_not_throw_when_casting_TokenUsage_to_AnthropicTokenUsage() {

        AnthropicChatResponseMetadata metadata = AnthropicChatResponseMetadata.builder()
                .id("id-1")
                .modelName("model-1")
                .tokenUsage(new TokenUsage(1, 2, 3))
                .build();

        assertThat(metadata.tokenUsage().inputTokenCount()).isEqualTo(1);
        assertThat(metadata.tokenUsage().outputTokenCount()).isEqualTo(2);
        assertThat(metadata.toString()).contains("model-1");
    }

    @Test
    void should_return_null_token_usage_when_absent() {

        AnthropicChatResponseMetadata metadata =
                AnthropicChatResponseMetadata.builder().id("id-1").build();

        assertThat(metadata.tokenUsage()).isNull();
    }

    @Test
    void should_return_provider_token_usage_unchanged() {

        AnthropicTokenUsage tokenUsage = AnthropicTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .build();

        AnthropicChatResponseMetadata metadata =
                AnthropicChatResponseMetadata.builder().tokenUsage(tokenUsage).build();

        assertThat(metadata.tokenUsage()).isSameAs(tokenUsage);
    }
}
