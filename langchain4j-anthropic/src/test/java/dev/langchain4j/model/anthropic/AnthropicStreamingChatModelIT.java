package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.OBJECT;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.property;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.anthropic.AnthropicChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.anthropic.AnthropicChatModelIT.randomString;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicStreamingChatModelIT {

    StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
            .apiKey(getenv("ANTHROPIC_API_KEY"))
            .maxTokens(20)
            .logRequests(true)
            .logResponses(true)
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER)
            .addParameter("second", INTEGER)
            .build();

    ToolSpecification weather = ToolSpecification.builder()
            .name("weather")
            .description("returns a weather forecast for a given location")
            // TODO simplify defining nested properties
            .addParameter("location", OBJECT, property("properties", singletonMap("city", singletonMap("type", "string"))))
            .build();


    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).contains("Berlin");

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
        StreamingChatLanguageModel visionModel = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .maxTokens(20)
                .logRequests(false) // base64-encoded images are huge
                .logResponses(true)
                .build();

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_enum_model_names(AnthropicChatModelName modelName) {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "Hi";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @Test
    void test_all_parameters() {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
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
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).isNotBlank();
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

        SystemMessage systemMessage = SystemMessage.from("What types of messages are supported in LangChain?".repeat(172) + randomString(2));
        UserMessage userMessage = new UserMessage(TextContent.from("What types of messages are supported in LangChain?"));

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(asList(userMessage, systemMessage), handler);
        AnthropicTokenUsage responseAnthropicTokenUsage = (AnthropicTokenUsage) handler.get().tokenUsage();

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
                .build())
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), List.of(toolSpecification), handler);
        AnthropicTokenUsage responseAnthropicTokenUsage = (AnthropicTokenUsage) handler.get().tokenUsage();

        // then
        assertThat(responseAnthropicTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(responseAnthropicTokenUsage.cacheReadInputTokens()).isEqualTo(0);
    }

    @Test
    void should_fail_to_create_without_api_key() {

        assertThatThrownBy(() -> AnthropicStreamingChatModel.withApiKey(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic API key must be defined. " +
                        "It can be generated here: https://console.anthropic.com/settings/keys");
    }

    @Test
    void should_execute_a_tool_then_stream_answer() {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), toolSpecifications, handler);

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, toolSpecifications, secondHandler);
        Response<AiMessage> secondResponse = secondHandler.get();

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void must_execute_a_tool() {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("2+2=?");
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        // when
        model.generate(singletonList(userMessage), calculator, handler);

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();

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
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessage = systemMessage("Do not think, nor explain step by step what you do. Output the result only.");
        UserMessage userMessage = userMessage("How much is 2+2 and 3+3? Call tools in parallel!");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(asList(systemMessage, userMessage), toolSpecifications, handler);

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();

        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 = aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        List<ChatMessage> messages = asList(systemMessage, userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, toolSpecifications, secondHandler);
        Response<AiMessage> secondResponse = secondHandler.get();

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_a_tool_with_nested_properties_then_answer() {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the weather in Berlin in Celsius?");
        List<ToolSpecification> toolSpecifications = singletonList(weather);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), toolSpecifications, handler);

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("weather");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"location\": {\"city\": \"Berlin\"}}");

        assertTokenUsage(response.tokenUsage());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "Super hot, 42 Celsius");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, toolSpecifications, secondHandler);
        Response<AiMessage> secondResponse = secondHandler.get();

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("42");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    private static void assertTokenUsage(@NotNull TokenUsage tokenUsage) {
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
