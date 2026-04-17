package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Run manually. Requires ANTHROPIC_API_KEY and costs real tokens.")
class AnthropicChatModelThinkingDisplayIT {

    private static String apiKey() {
        return System.getenv("ANTHROPIC_API_KEY");
    }

    private static String baseUrl() {
        String url = System.getenv("ANTHROPIC_BASE_URL");
        return url != null ? url : "https://api.anthropic.com/v1/";
    }

    @Test
    void should_return_thinking_when_display_is_summarized() {

        // given
        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(baseUrl())
                .apiKey(apiKey())
                .modelName("claude-opus-4-6")
                .thinkingType("adaptive")
                .thinkingDisplay("summarized")
                .maxTokens(4096)
                .returnThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is 15 * 37? Think step by step.");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).contains("555");
        assertThat(aiMessage.thinking()).isNotBlank();

        // verify display field was sent in the request
        assertThat(spyingHttpClient.requests().get(0).body())
                .contains("\"display\"")
                .contains("summarized");
    }

    @Test
    void should_return_empty_thinking_when_display_is_omitted() {

        // given
        ChatModel model = AnthropicChatModel.builder()
                .baseUrl(baseUrl())
                .apiKey(apiKey())
                .modelName("claude-opus-4-6")
                .thinkingType("adaptive")
                .thinkingDisplay("omitted")
                .maxTokens(4096)
                .returnThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is 15 * 37? Think step by step.");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).contains("555");
        // thinking should be blank or null since display is omitted
        assertThat(aiMessage.thinking()).isNullOrEmpty();
    }

    @Test
    void should_send_display_field_in_request_body() {

        // given
        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(baseUrl())
                .apiKey(apiKey())
                .modelName("claude-opus-4-6")
                .thinkingType("adaptive")
                .thinkingDisplay("summarized")
                .maxTokens(4096)
                .returnThinking(true)
                .build();

        UserMessage userMessage = UserMessage.from("Hello");

        // when
        model.chat(userMessage);

        // then - verify the thinking object in the request contains the display field
        String requestBody = spyingHttpClient.requests().get(0).body();
        assertThat(requestBody).contains("\"type\" : \"adaptive\"");
        assertThat(requestBody).contains("\"display\" : \"summarized\"");
    }
}
