package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiGeminiStreamingChatModelThinkingIT {

    private static final String MODEL_NAME = envOrDefault("VERTEX_AI_GEMINI_THINKING_MODEL", "gemini-3.1-pro-preview");
    private static final String LOCATION = envOrDefault("VERTEX_AI_GEMINI_THINKING_LOCATION", "global");
    private static final String API_ENDPOINT =
            envOrDefault("VERTEX_AI_GEMINI_THINKING_API_ENDPOINT", "aiplatform.googleapis.com");

    @Test
    void should_return_and_send_thought_signatures_with_tools() {
        // given
        boolean returnThinking = true;
        boolean sendThinking = true;

        StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(LOCATION)
                .apiEndpoint(API_ENDPOINT)
                .modelName(MODEL_NAME)
                .temperature(0.0f)
                .topK(1)
                .returnThinking(returnThinking)
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
        TestStreamingChatResponseHandler toolCallHandler = new TestStreamingChatResponseHandler();
        model.chat(toolCallRequest, toolCallHandler);
        ChatResponse toolCallResponse = toolCallHandler.get();

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
        ChatRequest finalRequest = ChatRequest.builder()
                .messages(userMessage, toolCallMessage, toolResultMessage)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        TestStreamingChatResponseHandler finalHandler = new TestStreamingChatResponseHandler();
        model.chat(finalRequest, finalHandler);
        ChatResponse finalResponse = finalHandler.get();

        // then
        assertThat(finalResponse.aiMessage().text()).containsIgnoringCase("sun");
        assertThat(finalResponse.aiMessage().toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_return_and_send_thought_signatures_across_multiple_tool_rounds() {
        // given
        boolean returnThinking = true;
        boolean sendThinking = true;

        StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(LOCATION)
                .apiEndpoint(API_ENDPOINT)
                .modelName(MODEL_NAME)
                .temperature(0.0f)
                .topK(1)
                .returnThinking(returnThinking)
                .sendThinking(sendThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        ToolSpecification toolSpecification = weatherTool();

        // --- Round 1: tool call for Munich ---
        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");
        ChatRequest request1 = ChatRequest.builder()
                .messages(userMessage1)
                .toolSpecifications(toolSpecification)
                .build();

        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        model.chat(request1, handler1);
        AiMessage aiMessage1 = handler1.get().aiMessage();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage1.attributes()).isNotEmpty();

        ToolExecutionRequest toolRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolRequest1.arguments()).contains("Munich");

        // --- Round 2: tool result → final answer for Munich ---
        ToolExecutionResultMessage toolResult1 = ToolExecutionResultMessage.from(toolRequest1, "sunny");
        ChatRequest request2 = ChatRequest.builder()
                .messages(userMessage1, aiMessage1, toolResult1)
                .toolSpecifications(toolSpecification)
                .build();

        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(request2, handler2);
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // --- Round 3: new question → tool call for Paris ---
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");
        ChatRequest request3 = ChatRequest.builder()
                .messages(userMessage1, aiMessage1, toolResult1, aiMessage2, userMessage2)
                .toolSpecifications(toolSpecification)
                .build();

        TestStreamingChatResponseHandler handler3 = new TestStreamingChatResponseHandler();
        model.chat(request3, handler3);
        AiMessage aiMessage3 = handler3.get().aiMessage();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage3.attributes()).isNotEmpty();

        ToolExecutionRequest toolRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolRequest2.arguments()).contains("Paris");

        // --- Round 4: tool result → final answer for Paris ---
        ToolExecutionResultMessage toolResult2 = ToolExecutionResultMessage.from(toolRequest2, "rainy");
        ChatRequest request4 = ChatRequest.builder()
                .messages(userMessage1, aiMessage1, toolResult1, aiMessage2, userMessage2, aiMessage3, toolResult2)
                .toolSpecifications(toolSpecification)
                .build();

        TestStreamingChatResponseHandler handler4 = new TestStreamingChatResponseHandler();
        model.chat(request4, handler4);
        AiMessage aiMessage4 = handler4.get().aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_NOT_send_thought_signatures_when_sendThinking_is_false() {
        // given
        boolean returnThinking = true;
        boolean sendThinking = false;

        StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(LOCATION)
                .apiEndpoint(API_ENDPOINT)
                .modelName(MODEL_NAME)
                .temperature(0.0f)
                .topK(1)
                .returnThinking(returnThinking)
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
        TestStreamingChatResponseHandler toolCallHandler = new TestStreamingChatResponseHandler();
        model.chat(toolCallRequest, toolCallHandler);
        ChatResponse toolCallResponse = toolCallHandler.get();

        // then — thought signatures are returned but will NOT be sent in follow-up
        AiMessage toolCallMessage = toolCallResponse.aiMessage();
        assertThat(toolCallMessage.toolExecutionRequests()).hasSize(1);
        assertThat(toolCallMessage.attributes()).isNotEmpty();

        ToolExecutionRequest toolExecutionRequest =
                toolCallMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest.arguments()).contains("Munich");

        // given — follow-up without thought signatures
        ToolExecutionResultMessage toolResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "sunny");
        ChatRequest finalRequest = ChatRequest.builder()
                .messages(userMessage, toolCallMessage, toolResultMessage)
                .toolSpecifications(toolSpecification)
                .build();

        // when/then — Gemini 3 requires thought signatures; follow-up is expected to fail
        TestStreamingChatResponseHandler finalHandler = new TestStreamingChatResponseHandler();
        assertThatThrownBy(() -> {
                    model.chat(finalRequest, finalHandler);
                    finalHandler.get();
                })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("thought_signature");
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
