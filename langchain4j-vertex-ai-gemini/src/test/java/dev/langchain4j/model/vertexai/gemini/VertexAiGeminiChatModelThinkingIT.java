package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiGeminiChatModelThinkingIT {

    private static final String MODEL_NAME = envOrDefault("VERTEX_AI_GEMINI_THINKING_MODEL", "gemini-3.1-pro-preview");
    private static final String LOCATION = envOrDefault("VERTEX_AI_GEMINI_THINKING_LOCATION", "global");
    private static final String API_ENDPOINT =
            envOrDefault("VERTEX_AI_GEMINI_THINKING_API_ENDPOINT", "aiplatform.googleapis.com");

    @Test
    void should_return_and_send_thought_signatures_with_tools() {
        // given
        boolean returnThinking = true;
        boolean sendThinking = true;

        ChatModel toolCallingModel = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(LOCATION)
                .apiEndpoint(API_ENDPOINT)
                .modelName(MODEL_NAME)
                .temperature(0.0f)
                .topK(1)
                .toolCallingMode(ToolCallingMode.ANY)
                .allowedFunctionNames(List.of("getWeather"))
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatModel followUpModel = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(LOCATION)
                .apiEndpoint(API_ENDPOINT)
                .modelName(MODEL_NAME)
                .temperature(0.0f)
                .topK(1)
                .sendThinking(sendThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        ToolSpecification toolSpecification = weatherTool();
        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");
        ChatRequest toolCallRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        ChatResponse toolCallResponse = toolCallingModel.chat(toolCallRequest);

        // then
        AiMessage toolCallMessage = toolCallResponse.aiMessage();
        assertThat(toolCallMessage.toolExecutionRequests()).hasSize(1);
        assertThat(toolCallMessage.attributes()).isNotEmpty();

        ToolExecutionRequest toolExecutionRequest =
                toolCallMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest.arguments()).contains("Munich");

        // given
        ToolExecutionResultMessage toolResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "sunny");

        // when
        ChatResponse finalResponse = followUpModel.chat(userMessage, toolCallMessage, toolResultMessage);

        // then
        assertThat(finalResponse.aiMessage().text()).containsIgnoringCase("sun");
        assertThat(finalResponse.aiMessage().toolExecutionRequests()).isEmpty();
    }

    private static ToolSpecification weatherTool() {
        return ToolSpecification.builder()
                .name("getWeather")
                .description("Returns the current weather for a city.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
