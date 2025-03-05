package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.OBJECT;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.property;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicChatModelIT {

    static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    ChatLanguageModel model = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .maxTokens(20)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatLanguageModel visionModel = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .maxTokens(20)
            .logRequests(false) // base64-encoded images are huge
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
            .addParameter(
                    "location", OBJECT, property("properties", singletonMap("city", singletonMap("type", "string"))))
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

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
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        ChatResponse response = visionModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_not_accept_image_url() {

        // given
        ImageContent imageAsURL = ImageContent.from(CAT_IMAGE_URL);

        UserMessage userMessage = UserMessage.from(imageAsURL);

        // when-then
        assertThatThrownBy(() -> visionModel.chat(userMessage))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Anthropic does not support images as URLs, only as Base64-encoded strings");
    }

    @Test
    void should_accept_text_and_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));

        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply in one word."), ImageContent.from(base64Data, "image/png"));

        // when
        ChatResponse response = visionModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_respect_maxTokens() {

        // given
        int maxTokens = 3;

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .maxTokens(maxTokens)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxTokens);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_respect_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("You are a professional translator into German language");
        UserMessage userMessage = UserMessage.from("Translate: I love you");

        // when
        ChatResponse response = model.chat(systemMessage, userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("liebe");
    }

    @Test
    void should_respect_stop_sequences() {

        // given
        List<String> stopSequences = singletonList("World");

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .stopSequences(stopSequences)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Say 'Hello World'");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("hello");
        assertThat(response.aiMessage().text()).doesNotContainIgnoringCase("world");

        assertThat(response.finishReason()).isEqualTo(OTHER);
    }

    @Test
    void should_cache_system_message() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
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
        ChatResponse response = model.chat(systemMessage, userMessage);

        // then
        AnthropicTokenUsage createCacheTokenUsage = (AnthropicTokenUsage) response.tokenUsage();
        assertThat(createCacheTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(createCacheTokenUsage.cacheReadInputTokens()).isEqualTo(0);

        // when
        ChatResponse response2 = model.chat(systemMessage, userMessage);

        // then
        AnthropicTokenUsage readCacheTokenUsage = (AnthropicTokenUsage) response2.tokenUsage();
        assertThat(readCacheTokenUsage.cacheCreationInputTokens()).isEqualTo(0);
        assertThat(readCacheTokenUsage.cacheReadInputTokens()).isGreaterThan(0);
    }

    @Test
    void should_cache_multiple_system_messages() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .beta("prompt-caching-2024-07-31")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .cacheSystemMessages(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessage =
                SystemMessage.from("What types of messages are supported in LangChain?".repeat(87) + randomString(2));
        SystemMessage systemMessage2 =
                SystemMessage.from("What types of messages are supported in LangChain?".repeat(87) + randomString(2));
        UserMessage userMessage =
                new UserMessage(TextContent.from("What types of messages are supported in LangChain?"));

        // when
        ChatResponse response = model.chat(systemMessage, systemMessage2, userMessage);

        // then
        AnthropicTokenUsage createCacheTokenUsage = (AnthropicTokenUsage) response.tokenUsage();
        assertThat(createCacheTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(createCacheTokenUsage.cacheReadInputTokens()).isEqualTo(0);

        // when
        ChatResponse response2 = model.chat(systemMessage, systemMessage2, userMessage);

        // then
        AnthropicTokenUsage readCacheTokenUsage = (AnthropicTokenUsage) response2.tokenUsage();
        assertThat(readCacheTokenUsage.cacheCreationInputTokens()).isEqualTo(0);
        assertThat(readCacheTokenUsage.cacheReadInputTokens()).isGreaterThan(0);
    }

    @Test
    void should_fail_if_more_than_four_system_message_with_cache() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .beta("prompt-caching-2024-07-31")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .cacheSystemMessages(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessageOne = SystemMessage.from("banana");
        SystemMessage systemMessageTwo = SystemMessage.from("banana");
        SystemMessage systemMessageThree = SystemMessage.from("banana");
        SystemMessage systemMessageFour = SystemMessage.from("banana");
        SystemMessage systemMessageFive = SystemMessage.from("banana");

        // then
        assertThatThrownBy(() -> model.chat(
                systemMessageOne, systemMessageTwo, systemMessageThree, systemMessageFour, systemMessageFive))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage(
                        "dev.langchain4j.model.anthropic.internal.client.AnthropicHttpException: "
                                + "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"messages: at least one message is required\"}}");
    }

    @Test
    void all_parameters() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
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
                .maxRetries(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_enum_model_names(AnthropicChatModelName modelName) {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_string_model_names(AnthropicChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelNameString)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_fail_to_create_without_api_key() {

        assertThatThrownBy(() -> AnthropicChatModel.builder().apiKey(null).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic API key must be defined. "
                        + "It can be generated here: https://console.anthropic.com/settings/keys");
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        UserMessage userMessage = userMessage("2+2=?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(userMessage, aiMessage, toolExecutionResultMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse secondResponse = model.chat(secondRequest);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_cache_system_message_and_tools() {

        // given
        AnthropicChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .beta("prompt-caching-2024-07-31")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .cacheSystemMessages(true)
                .cacheTools(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        SystemMessage systemMessage = SystemMessage.from("returns a sum of two numbers".repeat(210) + randomString(2));

        UserMessage userMessage = userMessage("How much is 2+2 and 3+3? Call tools in parallel!");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("calculator")
                .description(randomString(2))
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("first")
                        .addIntegerProperty("second")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AnthropicTokenUsage createCacheTokenUsage = (AnthropicTokenUsage) response.tokenUsage();
        assertThat(createCacheTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(createCacheTokenUsage.cacheReadInputTokens()).isEqualTo(0);

        // when
        ChatResponse response2 = model.chat(request);

        // then
        AnthropicTokenUsage readCacheTokenUsage = (AnthropicTokenUsage) response2.tokenUsage();
        assertThat(readCacheTokenUsage.cacheCreationInputTokens()).isEqualTo(0);
        assertThat(readCacheTokenUsage.cacheReadInputTokens()).isGreaterThan(0);
    }

    @Test
    void should_cache_tools() {

        // given
        AnthropicChatModel model = AnthropicChatModel.builder()
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

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AnthropicTokenUsage createCacheTokenUsage = (AnthropicTokenUsage) response.tokenUsage();
        assertThat(createCacheTokenUsage.cacheCreationInputTokens()).isGreaterThan(0);
        assertThat(createCacheTokenUsage.cacheReadInputTokens()).isEqualTo(0);

        // when
        ChatResponse response2 = model.chat(request);

        // then
        AnthropicTokenUsage readCacheTokenUsage = (AnthropicTokenUsage) response2.tokenUsage();
        assertThat(readCacheTokenUsage.cacheCreationInputTokens()).isEqualTo(0);
        assertThat(readCacheTokenUsage.cacheReadInputTokens()).isGreaterThan(0);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("How much is 2+2 and 3+3? Call tools in parallel!");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = from(toolExecutionRequest2, "6");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2)
                .toolSpecifications(calculator)
                .build();

        // when
        ChatResponse secondResponse = model.chat(secondRequest);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_a_tool_with_nested_properties_then_answer() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the weather in Munich?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(weather)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("weather");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"location\": {\"city\": \"Munich\"}}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "Super hot, 42 Celsius");

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(userMessage, aiMessage, toolExecutionResultMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        ChatResponse secondResponse = model.chat(secondRequest);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("42");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    static String randomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(characters.charAt(random.nextInt(characters.length())));
        }
        return result.toString();
    }
}
