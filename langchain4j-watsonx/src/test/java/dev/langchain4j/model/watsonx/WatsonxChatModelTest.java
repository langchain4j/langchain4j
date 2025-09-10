package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ControlMessage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.FinishReason;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WatsonxChatModelTest {

    @Mock
    ChatService mockChatService;

    @Mock
    ChatService.Builder mockChatServiceBuilder;

    @Captor
    ArgumentCaptor<com.ibm.watsonx.ai.chat.ChatRequest> chatRequestCaptor;

    static ChatResponse chatResponse;

    @BeforeEach
    void setUp() {

        when(mockChatServiceBuilder.modelId(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.url(any(URI.class))).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.projectId(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.spaceId(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.timeout(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.version(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.logRequests(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.logResponses(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.build()).thenReturn(mockChatService);

        chatResponse = new ChatResponse();
        var chatUsage = new ChatUsage();

        chatUsage.setCompletionTokens(10);
        chatUsage.setPromptTokens(10);
        chatUsage.setTotalTokens(20);

        chatResponse.setId("id");
        chatResponse.setModelId("modelId");
        chatResponse.setModel("model");
        chatResponse.setModelVersion("modelVersion");
        chatResponse.setObject("object");
        chatResponse.setUsage(chatUsage);
        chatResponse.setCreatedAt("createdAt");
        chatResponse.setCreated(1L);
    }

    @Test
    void testWatsonxChatModelBuilder() {

        var chatModel = WatsonxChatModel.builder()
                .url(CloudRegion.FRANKFURT)
                .modelName("model-name")
                .apiKey("api-key-test")
                .projectId("project-id")
                .spaceId("space-id")
                .version("my-version")
                .logRequests(true)
                .logResponses(true)
                .build();

        var defaultRequestParameters =
                assertInstanceOf(WatsonxChatRequestParameters.class, chatModel.defaultRequestParameters());
        assertNull(defaultRequestParameters.frequencyPenalty());
        assertNull(defaultRequestParameters.logitBias());
        assertNull(defaultRequestParameters.logprobs());
        assertNull(defaultRequestParameters.maxOutputTokens());
        assertEquals("model-name", defaultRequestParameters.modelName());
        assertNull(defaultRequestParameters.presencePenalty());
        assertEquals("project-id", defaultRequestParameters.projectId());
        assertNull(defaultRequestParameters.responseFormat());
        assertNull(defaultRequestParameters.seed());
        assertEquals("space-id", defaultRequestParameters.spaceId());
        assertEquals(List.of(), defaultRequestParameters.stopSequences());
        assertNull(defaultRequestParameters.temperature());
        assertNull(defaultRequestParameters.timeLimit());
        assertNull(defaultRequestParameters.toolChoice());
        assertNull(defaultRequestParameters.toolChoiceName());
        assertEquals(List.of(), defaultRequestParameters.toolSpecifications());
        assertNull(defaultRequestParameters.topK());
        assertNull(defaultRequestParameters.topLogprobs());
        assertNull(defaultRequestParameters.topP());

        assertDoesNotThrow(() -> WatsonxChatModel.builder()
                .url("https://test.com")
                .modelName("model-name")
                .authenticationProvider(
                        IAMAuthenticator.builder().apiKey("api-key").build())
                .projectId("project-id")
                .spaceId("space-id")
                .build());
    }

    @Test
    void testDoChat() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            assertEquals("Hello", chatModel.chat("hello"));
            assertEquals(
                    List.of(UserMessage.text("hello")),
                    chatRequestCaptor.getValue().getMessages());
        });
    }

    @Test
    void testDoChatWithRefusal() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            assertThrows(ContentFilteredException.class, () -> chatModel.chat("hello"), "refusal");
        });
    }

    @Test
    void testDoChatWithThinking() throws Exception {

        // --- TEST 1 ---

        var extractionTags = ExtractionTags.of("think", "response");
        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE, "<think>I'm thinking</think><response>Hello</response>", null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");

        var field = ChatResponse.class.getDeclaredField("extractionTags");
        field.setAccessible(true);
        field.set(chatResponse, extractionTags);
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .thinking(extractionTags)
                    .build();

            var result = chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build());
            assertEquals("Hello", result.aiMessage().text());
            assertEquals("I'm thinking", result.aiMessage().thinking());
            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().getMessages().get(0));
            assertEquals(
                    ControlMessage.of("thinking"),
                    chatRequestCaptor.getValue().getMessages().get(1));
        });
        // --------------

        // --- TEST 2 ---
        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .thinking(ExtractionTags.of("think"))
                    .build();

            assertThrows(
                    LangChain4jException.class,
                    () -> chatModel.chat(ChatRequest.builder()
                            .messages(
                                    dev.langchain4j.data.message.SystemMessage.from("You are an helpful assistant"),
                                    dev.langchain4j.data.message.UserMessage.from("Hello"))
                            .build()),
                    "The thinking/reasoning cannot be activated when a system message is present");
        });
        // --------------

        // --- TEST 3 ---
        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .thinking(ExtractionTags.of("think"))
                    .toolSpecifications(ToolSpecification.builder().name("test").build())
                    .build();

            assertThrows(
                    LangChain4jException.class,
                    () -> chatModel.chat(ChatRequest.builder()
                            .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                            .build()),
                    "The thinking/reasoning cannot be activated when tools are used");
        });
        // --------------
    }

    @Test
    void testDoChatWithTool() {

        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, null, List.of(toolCall));
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));
        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("hello"))
                    .toolSpecifications(ToolSpecification.builder()
                            .name("name")
                            .description("description")
                            .parameters(JsonObjectSchema.builder()
                                    .addStringProperty("string")
                                    .required("string")
                                    .build())
                            .build())
                    .build();

            var response = chatModel.chat(chatRequest);
            var metadata = (WatsonxChatResponseMetadata) response.metadata();
            assertEquals("id", response.id());
            assertEquals("modelId", response.modelName());
            assertEquals("modelVersion", metadata.getModelVersion());
            assertEquals(1L, metadata.getCreated());
            assertEquals(FinishReason.STOP, response.finishReason());
            assertEquals(10, response.tokenUsage().inputTokenCount());
            assertEquals(10, response.tokenUsage().outputTokenCount());
            assertEquals(20, response.tokenUsage().totalTokenCount());
            assertTrue(response.aiMessage().hasToolExecutionRequests());
            assertEquals(1, response.aiMessage().toolExecutionRequests().size());
            assertEquals(
                    "name", response.aiMessage().toolExecutionRequests().get(0).name());
            assertEquals(
                    "id", response.aiMessage().toolExecutionRequests().get(0).id());
            assertEquals(
                    "{}", response.aiMessage().toolExecutionRequests().get(0).arguments());
        });
    }

    @Test
    void testChatRequest() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .listeners(List.of(new ChatModelListener() {}))
                    .build();

            var chatRequest = ChatRequest.builder()
                    .modelName("customModelName")
                    .frequencyPenalty(0.10)
                    .maxOutputTokens(10)
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .presencePenalty(0.10)
                    .responseFormat(ResponseFormat.JSON)
                    .stopSequences(List.of("stop"))
                    .temperature(0.10)
                    .toolChoice(ToolChoice.REQUIRED)
                    .toolSpecifications(ToolSpecification.builder().name("name").build())
                    .topP(0.10)
                    .build();

            chatModel.chat(chatRequest);
            assertEquals(
                    List.<ChatMessage>of(UserMessage.text("Hello")),
                    chatRequestCaptor.getValue().getMessages());

            var parameters = chatRequestCaptor.getValue().getParameters();
            assertEquals(1, chatModel.listeners().size());
            assertNotNull(chatModel.listeners().get(0));
            assertEquals("customModelName", parameters.getModelId());
            assertEquals(0.10, parameters.getFrequencyPenalty());
            assertEquals(10, parameters.getMaxCompletionTokens());
            assertEquals(0.10, parameters.getPresencePenalty());
            assertEquals("json_object", parameters.getResponseFormat());
            assertEquals(List.of("stop"), parameters.getStop());
            assertEquals(0.10, parameters.getTemperature());
            assertEquals("required", parameters.getToolChoiceOption());
            assertEquals(0.10, parameters.getTopP());
        });
    }

    @Test
    void testChatRequestParameter() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelName")
                    .projectId("projectId")
                    .spaceId("spaceId")
                    .apiKey("api-key")
                    .frequencyPenalty(0.1)
                    .maxOutputTokens(0)
                    .presencePenalty(0.2)
                    .stopSequences("[")
                    .temperature(0.3)
                    .toolChoice(ToolChoice.REQUIRED)
                    .responseFormat(ResponseFormat.TEXT)
                    .timeLimit(Duration.ofMillis(30))
                    .topP(0.4)
                    .logitBias(Map.of("test", 10))
                    .logprobs(true)
                    .seed(5)
                    .toolChoiceName("toolChoiceName")
                    .topLogprobs(10)
                    .toolSpecifications(
                            ToolSpecification.builder().name("toolChoiceName").build())
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().getParameters();

            assertEquals(0.1, parameters.getFrequencyPenalty());
            assertEquals(0, parameters.getMaxCompletionTokens());
            assertEquals("modelName", parameters.getModelId());
            assertEquals(0.2, parameters.getPresencePenalty());
            assertEquals(List.of("["), parameters.getStop());
            assertEquals(0.3, parameters.getTemperature());
            assertEquals(null, parameters.getToolChoiceOption());
            assertEquals(null, parameters.getResponseFormat());
            assertEquals(30, parameters.getTimeLimit());
            assertEquals(0.4, parameters.getTopP());
            assertEquals("projectId", parameters.getProjectId());
            assertEquals(Map.of("test", 10), parameters.getLogitBias());
            assertTrue(parameters.getLogprobs());
            assertEquals(5, parameters.getSeed());
            assertEquals("spaceId", parameters.getSpaceId());
            assertEquals(
                    Map.of("type", "function", "function", Map.of("name", "toolChoiceName")),
                    parameters.getToolChoice());
            assertEquals(10, parameters.getTopLogprobs());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .apiKey("api-key")
                    .defaultRequestParameters(WatsonxChatRequestParameters.builder()
                            .frequencyPenalty(0.1)
                            .maxOutputTokens(0)
                            .modelName("default-model-name")
                            .presencePenalty(0.2)
                            .stopSequences("[")
                            .temperature(0.3)
                            .toolChoice(ToolChoice.REQUIRED)
                            .responseFormat(ResponseFormat.TEXT)
                            .timeLimit(Duration.ofMillis(30))
                            .topP(0.4)
                            .projectId("default-project-id")
                            .logitBias(Map.of("test", 10))
                            .logprobs(true)
                            .seed(5)
                            .spaceId("default-space-id")
                            .toolChoiceName("toolChoiceName")
                            .toolSpecifications(
                                    ToolSpecification.builder().name("test").build())
                            .topLogprobs(10)
                            .build())
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("toolChoiceName").build())
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().getParameters();

            assertEquals(0.1, parameters.getFrequencyPenalty());
            assertEquals(0, parameters.getMaxCompletionTokens());
            assertEquals("default-model-name", parameters.getModelId());
            assertEquals(0.2, parameters.getPresencePenalty());
            assertEquals(List.of("["), parameters.getStop());
            assertEquals(0.3, parameters.getTemperature());
            assertEquals(null, parameters.getToolChoiceOption());
            assertEquals(null, parameters.getResponseFormat());
            assertEquals(30, parameters.getTimeLimit());
            assertEquals(0.4, parameters.getTopP());
            assertEquals("default-project-id", parameters.getProjectId());
            assertEquals(Map.of("test", 10), parameters.getLogitBias());
            assertTrue(parameters.getLogprobs());
            assertEquals(5, parameters.getSeed());
            assertEquals("default-space-id", parameters.getSpaceId());
            assertEquals(
                    Map.of("type", "function", "function", Map.of("name", "toolChoiceName")),
                    parameters.getToolChoice());
            assertEquals(10, parameters.getTopLogprobs());
            assertNull(parameters.getToolChoiceOption());
        });

        withChatServiceMock(() -> {

            // TEST 1: Override paramaters
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .apiKey("api-key")
                    .defaultRequestParameters(WatsonxChatRequestParameters.builder()
                            .modelName("modelId")
                            .projectId("project-id")
                            .spaceId("space-id")
                            .frequencyPenalty(0.1)
                            .maxOutputTokens(0)
                            .modelName("default-model-name")
                            .presencePenalty(0.2)
                            .stopSequences("[")
                            .temperature(0.3)
                            .toolChoice(ToolChoice.REQUIRED)
                            .responseFormat(ResponseFormat.TEXT)
                            .timeLimit(Duration.ofMillis(30))
                            .topP(0.4)
                            .projectId("default-project-id")
                            .logitBias(Map.of("test", 10))
                            .logprobs(true)
                            .seed(5)
                            .spaceId("default-space-id")
                            .toolChoiceName("toolChoiceName")
                            .toolSpecifications(
                                    ToolSpecification.builder().name("test").build())
                            .topLogprobs(10)
                            .build())
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();

            var chatRequest = ChatRequest.builder()
                    .modelName("customModelName")
                    .frequencyPenalty(0.10)
                    .maxOutputTokens(10)
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .presencePenalty(0.10)
                    .responseFormat(ResponseFormat.JSON)
                    .stopSequences(List.of("stop"))
                    .temperature(0.10)
                    .toolChoice(ToolChoice.REQUIRED)
                    .toolSpecifications(
                            ToolSpecification.builder().name("toolChoiceName").build())
                    .topP(0.10)
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().getParameters();

            assertEquals("customModelName", parameters.getModelId());
            assertEquals(0.10, parameters.getFrequencyPenalty());
            assertEquals(10, parameters.getMaxCompletionTokens());
            assertEquals(0.10, parameters.getPresencePenalty());
            assertEquals("json_object", parameters.getResponseFormat());
            assertEquals(List.of("stop"), parameters.getStop());
            assertEquals(0.10, parameters.getTemperature());
            assertEquals(null, parameters.getToolChoiceOption());
            assertEquals(0.10, parameters.getTopP());
            // ----------------
        });

        withChatServiceMock(() -> {

            // TEST 2: Override parameters
            var chatModel = WatsonxChatModel.builder()
                    .url("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .spaceId("space-id")
                    .apiKey("api-key")
                    .frequencyPenalty(0.1)
                    .maxOutputTokens(0)
                    .modelName("default-model-name")
                    .presencePenalty(0.2)
                    .stopSequences("[")
                    .temperature(0.3)
                    .toolChoice(ToolChoice.REQUIRED)
                    .responseFormat(ResponseFormat.TEXT)
                    .timeLimit(Duration.ofMillis(30))
                    .topP(0.4)
                    .projectId("default-project-id")
                    .logitBias(Map.of("test", 10))
                    .logprobs(true)
                    .seed(5)
                    .spaceId("default-space-id")
                    .toolChoiceName("toolChoiceName")
                    .toolSpecifications(ToolSpecification.builder().name("test").build())
                    .topLogprobs(10)
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .frequencyPenalty(0.2)
                            .maxOutputTokens(10)
                            .modelName("modelNames")
                            .presencePenalty(0.3)
                            .stopSequences("[]")
                            .temperature(0.4)
                            .toolChoice(ToolChoice.REQUIRED)
                            .responseFormat(JsonSchema.builder()
                                    .name("test")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("city")
                                            .build())
                                    .build())
                            .timeLimit(Duration.ofMillis(40))
                            .topP(0.5)
                            .projectId("projectIds")
                            .logitBias(Map.of("tests", 11))
                            .logprobs(false)
                            .seed(15)
                            .spaceId("spaceIds")
                            .toolChoiceName(null)
                            .toolSpecifications(ToolSpecification.builder()
                                    .name("toolChoiceName")
                                    .build())
                            .topLogprobs(11)
                            .build())
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().getParameters();

            assertEquals(0.2, parameters.getFrequencyPenalty());
            assertEquals(10, parameters.getMaxCompletionTokens());
            assertEquals("modelNames", parameters.getModelId());
            assertEquals(0.3, parameters.getPresencePenalty());
            assertEquals(List.of("[]"), parameters.getStop());
            assertEquals(0.4, parameters.getTemperature());
            assertEquals(null, parameters.getToolChoiceOption());
            assertEquals("json_schema", parameters.getResponseFormat());
            assertNotNull(parameters.getJsonSchema());
            assertEquals(40, parameters.getTimeLimit());
            assertEquals(0.5, parameters.getTopP());
            assertEquals("projectIds", parameters.getProjectId());
            assertEquals(Map.of("tests", 11), parameters.getLogitBias());
            assertFalse(parameters.getLogprobs());
            assertEquals(15, parameters.getSeed());
            assertEquals("spaceIds", parameters.getSpaceId());
            assertNotNull(parameters.getToolChoice());
            assertEquals(11, parameters.getTopLogprobs());
            assertNull(parameters.getToolChoiceOption());
            // ----------------
        });
    }

    @Test
    void testChatRequestWithTopK() {

        var chatModel = WatsonxChatModel.builder()
                .url("https://test.com")
                .modelName("modelId")
                .projectId("project-id")
                .spaceId("space-id")
                .apiKey("api-key")
                .build();

        assertThrows(
                UnsupportedFeatureException.class,
                () -> chatModel.chat(ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                        .topK(10)
                        .build()));

        assertThrows(UnsupportedFeatureException.class, () -> WatsonxChatModel.builder()
                .url("https://test.com")
                .modelName("modelId")
                .projectId("project-id")
                .spaceId("space-id")
                .apiKey("api-key")
                .defaultRequestParameters(
                        ChatRequestParameters.builder().topK(10).build())
                .build());
    }

    @Test
    void testSupportCapabilities() {

        var chatModel = WatsonxChatModel.builder()
                .url("https://test.com")
                .modelName("modelId")
                .projectId("project-id")
                .spaceId("space-id")
                .apiKey("api-key")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertEquals(1, chatModel.supportedCapabilities().size());
        assertTrue(chatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
    }

    private void withChatServiceMock(Runnable action) {
        try (MockedStatic<ChatService> mockedStatic = mockStatic(ChatService.class)) {
            mockedStatic.when(ChatService::builder).thenReturn(mockChatServiceBuilder);
            action.run();
        }
    }
}
