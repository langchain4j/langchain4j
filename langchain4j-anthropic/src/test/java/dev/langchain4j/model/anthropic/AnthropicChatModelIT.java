package dev.langchain4j.model.anthropic;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_5_20250929;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatModelIT {

    @Test
    void should_accept_base64_pdf() {

        // given
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .logRequests(false) // base64-encoded PDFs are huge
                .logResponses(true)
                .build();

        URI pdfUri = Paths.get("src/test/resources/test-file.pdf").toUri();
        String base64Data = new String(Base64.getEncoder().encode(readBytes(pdfUri.toString())));
        UserMessage userMessage = UserMessage.from(
                PdfFileContent.from(base64Data, "application/pdf"),
                TextContent.from("What is written in the document?"));

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("test content");
    }

    @Test
    void should_respect_stop_sequences() {

        // given
        List<String> stopSequences = List.of("World", " World");

        ChatModel model = AnthropicChatModel.builder()
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

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_cache_system_message() {

        // given
        ChatModel model = AnthropicChatModel.builder()
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
        ChatModel model = AnthropicChatModel.builder()
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
        ChatModel model = AnthropicChatModel.builder()
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
                .isExactlyInstanceOf(dev.langchain4j.exception.InvalidRequestException.class)
                .hasMessageContaining("messages: Field required");
    }

    @Test
    void all_parameters() {

        // given
        ChatModel model = AnthropicChatModel.builder()
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
                .maxRetries(2)
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
    @EnumSource(
            value = AnthropicChatModelName.class,
            mode = EXCLUDE,
            names = {"CLAUDE_OPUS_4_20250514" // Run manually before release. Expensive to run very often.
            })
    void should_support_all_enum_model_names(AnthropicChatModelName modelName) {

        // given
        ChatModel model = AnthropicChatModel.builder()
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
    @EnumSource(
            value = AnthropicChatModelName.class,
            mode = EXCLUDE,
            names = {"CLAUDE_OPUS_4_20250514" // Run manually before release. Expensive to run very often.
            })
    void should_support_all_string_model_names(AnthropicChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        ChatModel model = AnthropicChatModel.builder()
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
                .hasMessage("apiKey cannot be null or blank");
    }

    @Test
    void should_execute_one_specific_tool_and_ignore_another_tool_with_parallel_tool_disabled() {
        // given
        String expectedToolName = "get_weather";
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .toolChoiceName(expectedToolName)
                .disableParallelToolUse(true)
                .toolChoice(ToolChoice.REQUIRED)
                .build();

        ToolSpecification getWeather = ToolSpecification.builder()
                .name(expectedToolName)
                .description("Get the current weather in a given location")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location")
                        .required("location")
                        .build())
                .build();
        ToolSpecification getTime = ToolSpecification.builder()
                .name("get_time")
                .description("Get the current time in a given timezone")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("timezone")
                        .required("timezone")
                        .build())
                .build();

        List<ToolSpecification> toolSpecifications = List.of(getWeather, getTime);

        UserMessage userMessage = userMessage("What's the weather in SF and NYC, and what time is it there?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse response = model.chat(request);
        // then
        AiMessage aiMessage = response.aiMessage();
        ToolExecutionRequest tool = aiMessage.toolExecutionRequests().get(0);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        assertThat(tool.name()).isEqualTo(expectedToolName);
    }

    @Test
    void should_force_execution_without_tools_when_pass_tool_choice_none() {
        // given
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .toolChoice(ToolChoice.NONE)
                .build();

        ToolSpecification getWeather = ToolSpecification.builder()
                .name("get_weather")
                .description("Get the current weather in a given location")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location")
                        .required("location")
                        .build())
                .build();
        ToolSpecification getTime = ToolSpecification.builder()
                .name("get_time")
                .description("Get the current time in a given timezone")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("timezone")
                        .required("timezone")
                        .build())
                .build();

        List<ToolSpecification> toolSpecifications = List.of(getWeather, getTime);

        UserMessage userMessage = userMessage("What's the weather in SF and NYC, and what time is it there?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse response = model.chat(request);
        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_execute_one_tool_and_ignore_another_tool_with_parallel_tool_disabled() {
        // given
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .toolChoice(ToolChoice.REQUIRED)
                .disableParallelToolUse(true)
                .build();

        ToolSpecification getWeather = ToolSpecification.builder()
                .name("get_weather")
                .description("Get the current weather in a given location")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location")
                        .required("location")
                        .build())
                .build();
        ToolSpecification getTime = ToolSpecification.builder()
                .name("get_time")
                .description("Get the current time in a given timezone")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("timezone")
                        .required("timezone")
                        .build())
                .build();

        List<ToolSpecification> toolSpecifications = List.of(getWeather, getTime);

        UserMessage userMessage = userMessage("What's the weather in SF and NYC, and what time is it there?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse response = model.chat(request);
        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
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
                        .required("first", "second")
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
                        .required("first", "second")
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
    void should_allow_non_user_message_as_first_message_and_consecutive_user_messages() {

        // given
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(
                        AiMessage.from("Hi"),
                        UserMessage.from("What is the"),
                        UserMessage.from("capital of"),
                        UserMessage.from("Germany?"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_send_custom_parameters() {

        // given
        record Edit(String type) {}
        record ContextManagement(List<Edit> edits) { }
        Map<String, Object> customParameters = Map.of("context_management", new ContextManagement(List.of(new Edit("clear_tool_uses_20250919"))));

        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel model = AnthropicChatModel.builder()
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
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).contains("Berlin");

        assertThat(spyingHttpClient.request().body().contains("context_management"));
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
