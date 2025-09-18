package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

/**
 * Integration tests for AWS Bedrock prompt caching functionality.
 * These tests verify that prompt caching can be enabled and configured correctly.
 */
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockPromptCachingIT {

    private static final String NOVA_MODEL = "us.amazon.nova-micro-v1:0";

    @Test
    void should_chat_with_prompt_caching_enabled() {
        // Given
        BedrockChatRequestParameters requestParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .temperature(0.7)
                .maxOutputTokens(200)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .region(Region.US_EAST_1)
                .defaultRequestParameters(requestParams)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant that provides concise answers."),
                        UserMessage.from("What is prompt caching and how does it help?")))
                .build();

        // When
        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata().tokenUsage()).isNotNull();
    }

    @Test
    void should_chat_with_different_cache_point_placements() {
        // Test AFTER_USER_MESSAGE placement
        BedrockChatRequestParameters afterUserParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        ChatModel modelAfterUser = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(afterUserParams)
                .build();

        ChatRequest requestAfterUser = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("Explain caching in one sentence.")))
                .build();

        ChatResponse responseAfterUser = modelAfterUser.chat(requestAfterUser);

        assertThat(responseAfterUser).isNotNull();
        assertThat(responseAfterUser.aiMessage().text()).isNotBlank();
        assertThat(responseAfterUser.metadata().tokenUsage()).isNotNull();

        // Test AFTER_TOOLS placement (when tools are available)
        BedrockChatRequestParameters afterToolsParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_TOOLS)
                .build();

        ChatModel modelAfterTools = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(afterToolsParams)
                .build();

        ChatRequest requestAfterTools = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("What are the benefits of caching?")))
                .build();

        ChatResponse responseAfterTools = modelAfterTools.chat(requestAfterTools);

        assertThat(responseAfterTools).isNotNull();
        assertThat(responseAfterTools.aiMessage().text()).isNotBlank();
        assertThat(responseAfterTools.metadata().tokenUsage()).isNotNull();
    }

    @Test
    void should_chat_without_prompt_caching() {
        // Given - model without prompt caching
        ChatModel model = BedrockChatModel.builder().modelId(NOVA_MODEL).build();

        // When
        ChatResponse response = model.chat(UserMessage.from("Hello, how are you?"));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_override_prompt_caching_parameters() {
        // Given - default parameters with caching enabled
        BedrockChatRequestParameters defaultParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .temperature(0.5)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(defaultParams)
                .build();

        // When - override with different cache point
        BedrockChatRequestParameters overrideParams = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .temperature(0.8)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Test message"))
                .parameters(overrideParams)
                .build();

        ChatResponse response = model.chat(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_handle_multiple_messages_with_caching() {
        // Given
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(params)
                .build();

        // Simulate a conversation with multiple turns
        ChatRequest request1 = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful coding assistant."), UserMessage.from("What is Java?")))
                .build();

        ChatResponse response1 = model.chat(request1);
        assertThat(response1.aiMessage().text()).isNotBlank();

        // Second request with same system message (should benefit from caching)
        ChatRequest request2 = ChatRequest.builder()
                .messages(Arrays.asList(
                        SystemMessage.from("You are a helpful coding assistant."), UserMessage.from("What is Python?")))
                .build();

        ChatResponse response2 = model.chat(request2);
        assertThat(response2.aiMessage().text()).isNotBlank();

        // Verify both responses are valid
        assertThat(response1.metadata().tokenUsage()).isNotNull();
        assertThat(response2.metadata().tokenUsage()).isNotNull();
    }

    @Test
    void should_combine_prompt_caching_with_other_parameters() {
        // Given
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .temperature(0.3)
                .maxOutputTokens(150)
                .topP(0.9)
                .stopSequences(Arrays.asList("END", "STOP"))
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(NOVA_MODEL)
                .defaultRequestParameters(params)
                .build();

        // When
        ChatResponse response = model.chat(UserMessage.from("Write a short poem about caching. End with 'END'"));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata().tokenUsage()).isNotNull();
    }
}
