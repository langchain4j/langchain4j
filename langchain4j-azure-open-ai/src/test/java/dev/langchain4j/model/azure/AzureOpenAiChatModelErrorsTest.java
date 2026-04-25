package dev.langchain4j.model.azure;

import static dev.langchain4j.exception.IllegalArgumentExceptions.illegalArgument;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

class AzureOpenAiChatModelErrorsTest {

    @Test
    void should_throw_descriptive_exception_when_choices_is_empty() {
        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> model.chat("Hello"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no choices");
    }
}
