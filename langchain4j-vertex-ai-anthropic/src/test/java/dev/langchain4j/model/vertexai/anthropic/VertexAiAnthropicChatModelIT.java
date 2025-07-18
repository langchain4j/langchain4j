package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.RetryingTest;

/**
 * Integration tests for VertexAiAnthropicChatModel
 *
 * Prerequisites:
 * - Set GCP_PROJECT_ID environment variable with your Google Cloud project ID
 * - Set GCP_LOCATION environment variable with your preferred location (e.g., "us-central1")
 * - Ensure you have access to Claude models in Vertex AI Model Garden
 * - Authenticate with Google Cloud (gcloud auth application-default login)
 */
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicChatModelIT {

    private static final String SMALL_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private ChatModel model;

    @BeforeEach
    void setUp() {
        model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(1000)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (model instanceof AutoCloseable) {
            ((AutoCloseable) model).close();
        }
    }

    @RetryingTest(3)
    void should_generate_response() {
        // given
        UserMessage userMessage = UserMessage.from(SIMPLE_QUESTION);

        // when
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).containsIgnoringCase(EXPECTED_ANSWER);
        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);

        TokenUsage tokenUsage = response.metadata().tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
    }

    @RetryingTest(3)
    void should_handle_system_message() {
        // given
        SystemMessage systemMessage = SystemMessage.from(SYSTEM_MESSAGE + " Always respond in exactly 3 words.");
        UserMessage userMessage = UserMessage.from("What is AI?");

        // when
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(systemMessage, userMessage))
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();

        String[] words = response.aiMessage().text().trim().split("\\s+");
        assertThat(words).hasSize(3);
    }

    @RetryingTest(3)
    void should_handle_conversation() {
        // given
        UserMessage firstMessage = UserMessage.from("My name is John");
        AiMessage firstResponse = AiMessage.from("Hello John! Nice to meet you.");
        UserMessage secondMessage = UserMessage.from("What is my name?");

        // when
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(firstMessage, firstResponse, secondMessage))
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).containsIgnoringCase("John");
    }

    @Test
    void should_handle_image_input() {
        // given
        Image image = Image.builder()
                .base64Data(SMALL_IMAGE_BASE64)
                .mimeType("image/png")
                .build();

        UserMessage userMessage =
                UserMessage.from(ImageContent.from(image), TextContent.from("What do you see in this image?"));

        // when
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @RetryingTest(3)
    void should_respect_max_tokens() {
        // given
        ChatModel limitedModel = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(10)
                .build();

        UserMessage userMessage = UserMessage.from("Write a long essay about artificial intelligence");

        // when
        ChatResponse response = limitedModel.chat(
                ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);

        TokenUsage tokenUsage = response.metadata().tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(10);
    }

    @RetryingTest(3)
    void should_handle_tools() {
        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(TOOL_PARAMETER_NAME, TOOL_PARAMETER_DESCRIPTION)
                        .required(TOOL_PARAMETER_NAME)
                        .build())
                .build();

        UserMessage userMessage = UserMessage.from("What's the weather like in New York?");

        // when
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(userMessage))
                .toolSpecifications(List.of(weatherTool))
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();

        if (response.aiMessage().hasToolExecutionRequests()) {
            assertThat(response.aiMessage().toolExecutionRequests()).isNotEmpty();
            assertThat(response.aiMessage().toolExecutionRequests().get(0).name())
                    .isEqualTo(TOOL_NAME);
            assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        }
    }

    @Test
    void should_have_correct_provider() {
        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_build_with_all_parameters() {
        // given/when
        VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                .project(DEFAULT_PROJECT)
                .location(DEFAULT_LOCATION)
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(2048)
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .stopSequences(List.of("STOP", "END"))
                .logRequests(true)
                .logResponses(false)
                .build();

        // then
        assertNotNull(model);
        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @RetryingTest(3)
    void should_handle_multiple_images() {
        // given
        Image image1 = Image.builder()
                .base64Data(SMALL_IMAGE_BASE64)
                .mimeType("image/png")
                .build();

        Image image2 = Image.builder()
                .base64Data(SECOND_IMAGE_BASE64)
                .mimeType("image/png")
                .build();

        UserMessage userMessage = UserMessage.from(
                ImageContent.from(image1), ImageContent.from(image2), TextContent.from("Compare these two images"));

        // when
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @RetryingTest(3)
    void should_handle_multiple_tools() {
        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name(WEATHER_TOOL_NAME)
                .description("Get weather information")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location", "City name")
                        .required("location")
                        .build())
                .build();

        ToolSpecification calculatorTool = ToolSpecification.builder()
                .name(CALCULATOR_TOOL_NAME)
                .description("Perform calculations")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("expression", "Mathematical expression")
                        .required("expression")
                        .build())
                .build();

        UserMessage userMessage = UserMessage.from(TOOL_EXECUTION_PROMPT);

        // when
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(userMessage))
                .toolSpecifications(List.of(weatherTool, calculatorTool))
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
    }

    @RetryingTest(3)
    void should_handle_stop_sequences() {
        // given
        ChatModel modelWithStopSequences = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(1000)
                .stopSequences(List.of(STOP_WORD_1, STOP_WORD_2))
                .build();

        UserMessage userMessage = UserMessage.from("Count from 1 to 10, then say STOP");

        // when
        ChatResponse response = modelWithStopSequences.chat(
                ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).doesNotContain("STOP");
    }

    @Test
    void should_handle_large_input() {
        // given
        UserMessage userMessage = UserMessage.from(LARGE_INPUT_TEXT);

        // when/then - should not throw exception
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
    }

    @Test
    void should_handle_minimal_input_gracefully() {
        // given
        UserMessage userMessage = UserMessage.from("Hi");

        // when/then - should handle minimal input gracefully
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
    }

    @Test
    void should_handle_special_characters() {
        // given
        UserMessage userMessage = UserMessage.from("Process this text: " + SPECIAL_CHARACTERS);

        // when
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_handle_unicode_text() {
        // given
        UserMessage userMessage = UserMessage.from("Translate this: " + UNICODE_TEXT);

        // when
        ChatResponse response =
                model.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @RetryingTest(3)
    void should_handle_complex_conversation_flow() {
        // given
        UserMessage starter = UserMessage.from(CONVERSATION_STARTER);
        AiMessage response1 = AiMessage.from("AI is a fascinating field with many applications.");
        UserMessage followUp = UserMessage.from(FOLLOW_UP_QUESTION);
        AiMessage response2 = AiMessage.from("Key benefits include automation, data analysis, and decision support.");
        UserMessage contextual = UserMessage.from(CONTEXT_QUESTION);

        // when
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(List.of(starter, response1, followUp, response2, contextual))
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_validate_temperature_parameter() {
        // given/when
        VertexAiAnthropicChatModel modelWithTemp = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .temperature(0.0)
                .build();

        // then
        assertNotNull(modelWithTemp);
        assertThat(modelWithTemp.provider()).isEqualTo(ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_validate_top_p_parameter() {
        // given/when
        VertexAiAnthropicChatModel modelWithTopP = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .topP(0.5)
                .build();

        // then
        assertNotNull(modelWithTopP);
        assertThat(modelWithTopP.provider()).isEqualTo(ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_validate_top_k_parameter() {
        // given/when
        VertexAiAnthropicChatModel modelWithTopK = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .topK(20)
                .build();

        // then
        assertNotNull(modelWithTopK);
        assertThat(modelWithTopK.provider()).isEqualTo(ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_support_prompt_caching() {
        // given
        VertexAiAnthropicChatModel modelWithCaching = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .enablePromptCaching(true)
                .maxTokens(1000)
                .build();

        // when - using a long system message to trigger caching
        SystemMessage longSystemMessage =
                SystemMessage.from("You are a helpful assistant specialized in explaining complex topics. "
                        + "Your task is to provide clear, concise explanations that are easy to understand. "
                        + "Always provide examples when possible and break down complex concepts into simpler parts. "
                        + "This is a long system message that should be cached for efficiency.");
        UserMessage userMessage = UserMessage.from("What is artificial intelligence?");

        ChatResponse response = modelWithCaching.chat(ChatRequest.builder()
                .messages(List.of(longSystemMessage, userMessage))
                .build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);

        TokenUsage tokenUsage = response.metadata().tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
    }

    static class WeatherService {
        @Tool("Get the current weather for a location")
        String getWeather(@dev.langchain4j.agent.tool.P("The city and state") String location) {
            return "The weather in " + location + " is sunny with 75°F";
        }
    }
}
