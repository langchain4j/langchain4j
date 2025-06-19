package dev.langchain4j.model.anthropic;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.anthropic.AnthropicChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.anthropic.AnthropicChatModelIT.randomString;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_7_SONNET_20250219;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingChatModelIT {

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addIntegerProperty("first")
                    .addIntegerProperty("second")
                    .required("first", "second")
                    .build())
            .build();

    ToolSpecification weather = ToolSpecification.builder()
            .name("weather")
            .description("returns a weather forecast for a given location")
            .parameters(JsonObjectSchema.builder()
                    .addProperty(
                            "location",
                            JsonObjectSchema.builder()
                                    .addStringProperty("city")
                                    .required("city")
                                    .build())
                    .required("location")
                    .build())
            .build();

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_accept_base64_image() {

        // given
        StreamingChatModel visionModel = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .maxTokens(20)
                .logRequests(false) // base64-encoded images are huge
                .logResponses(true)
                .build();

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        visionModel.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
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

    @Test
    void should_execute_a_tool_then_stream_answer() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("2+2=?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);

        // then
        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(userMessage, aiMessage, toolExecutionResultMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();
        model.chat(secondRequest, secondHandler);
        ChatResponse secondResponse = secondHandler.get();

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertTokenUsage(secondResponse.tokenUsage());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void must_execute_a_tool() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("2+2=?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);

        // then
        ChatResponse response = handler.get();

        AiMessage aiMessage = response.aiMessage();
        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessage =
                systemMessage("Do not think, nor explain step by step what you do. Output the result only.");
        UserMessage userMessage = userMessage("How much is 2+2 and 3+3? Call tools in parallel!");

        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);

        // then
        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();

        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(
                        systemMessage, userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2)
                .toolSpecifications(calculator)
                .build();

        // when
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();
        model.chat(secondRequest, secondHandler);
        ChatResponse secondResponse = secondHandler.get();

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertTokenUsage(secondResponse.tokenUsage());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_a_tool_with_nested_properties_then_answer() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the weather in Berlin in Celsius?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(weather)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);

        // then
        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("weather");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"location\": {\"city\": \"Berlin\"}}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "Super hot, 42 Celsius");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(userMessage, aiMessage, toolExecutionResultMessage)
                .toolSpecifications(weather)
                .build();

        // when
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();
        model.chat(secondRequest, secondHandler);
        ChatResponse secondResponse = secondHandler.get();

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("42");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertTokenUsage(secondResponse.tokenUsage());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_answer_with_thinking() {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_7_SONNET_20250219)
                .thinkingType("enabled")
                .thinkingBudgetTokens(1024)
                .maxTokens(1024 + 100)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse chatResponse = handler.get();

        // then
        assertThat(chatResponse.aiMessage().text()).contains("Berlin");
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

    private static void assertTokenUsage(@NotNull TokenUsage tokenUsage) {
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
