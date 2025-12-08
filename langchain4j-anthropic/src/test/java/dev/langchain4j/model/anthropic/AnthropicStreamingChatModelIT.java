package dev.langchain4j.model.anthropic;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.anthropic.AnthropicChatModelIT.randomString;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_7_SONNET_20250219;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_5_20250929;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingChatModelIT {

    @ParameterizedTest
    @EnumSource(
            value = AnthropicChatModelName.class,
            mode = EXCLUDE,
            names = {"CLAUDE_OPUS_4_20250514" // Run manually before release. Expensive to run very often.
            })
    void should_support_all_enum_model_names(AnthropicChatModelName modelName) {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "Hi";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void all_parameters() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .baseUrl("https://api.anthropic.com/v1/")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .version("2023-06-01")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(1.0)
                .topP(1.0)
                .topK(1)
                .maxTokens(3)
                .stopSequences(asList("hello", "world"))
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_cache_system_message() {

        // given
        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .beta("prompt-caching-2024-07-31")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .cacheSystemMessages(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessage =
                SystemMessage.from("What types of messages are supported in LangChain?".repeat(172) + randomString(2));
        UserMessage userMessage =
                new UserMessage(TextContent.from("What types of messages are supported in LangChain?"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage, systemMessage), handler);
        AnthropicTokenUsage responseAnthropicTokenUsage =
                (AnthropicTokenUsage) handler.get().tokenUsage();

        // then
        assertThat(responseAnthropicTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(responseAnthropicTokenUsage.cacheReadInputTokens()).isEqualTo(0);
    }

    @Test
    void should_cache_tools() {

        // given
        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .beta("prompt-caching-2024-07-31")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .cacheTools(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("How much is 2+2 and 3+3? Call tools in parallel!");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers".repeat(214) + randomString(2))
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("first")
                        .addIntegerProperty("second")
                        .required("first", "second")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);
        AnthropicTokenUsage responseAnthropicTokenUsage =
                (AnthropicTokenUsage) handler.get().tokenUsage();

        // then
        assertThat(responseAnthropicTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(responseAnthropicTokenUsage.cacheReadInputTokens()).isEqualTo(0);
    }

    @Test
    void should_fail_to_create_without_api_key() {

        assertThatThrownBy(
                        () -> AnthropicStreamingChatModel.builder().apiKey(null).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey cannot be null or blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_7_SONNET_20250219)
                .logRequests(true)
                .logResponses(true)
                .timeout(timeout)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        model.chat("hi", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                futureError.completeExceptionally(new RuntimeException("onPartialResponse should not be called"));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureError.completeExceptionally(new RuntimeException("onCompleteResponse should not be called"));
            }

            @Override
            public void onError(Throwable error) {
                futureError.complete(error);
            }
        });

        Throwable error = futureError.get(5, SECONDS);

        assertThat(error).isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_work_with_userId() {
        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .userId("test-user-12345")
                .maxTokens(10)
                .build();

        String userMessage = "Say hello";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_send_custom_parameters() {

        // given
        Map<String, Object> customParameters = Map.of("context_management", Map.of("edits", List.of(Map.of("type", "clear_tool_uses_20250919"))));

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_SONNET_4_5_20250929)
                .beta("context-management-2025-06-27")
                .customParameters(customParameters)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        ChatResponse chatResponse = handler.get();

        // then
        assertThat(chatResponse.aiMessage().text()).contains("Berlin");

        assertThat(spyingHttpClient.request().body().contains("context_management"));
    }
}
