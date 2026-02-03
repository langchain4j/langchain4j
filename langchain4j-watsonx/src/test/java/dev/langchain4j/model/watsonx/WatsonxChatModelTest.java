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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.UserMessage;
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
import java.util.Set;
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

    static ChatResponse.Builder chatResponse;

    @BeforeEach
    void setUp() {

        when(mockChatServiceBuilder.modelId(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.baseUrl(any(URI.class))).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.projectId(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.spaceId(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.timeout(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.version(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.logRequests(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.logResponses(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.authenticator(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.apiKey(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.httpClient(any())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.verifySsl(anyBoolean())).thenReturn(mockChatServiceBuilder);
        when(mockChatServiceBuilder.build()).thenReturn(mockChatService);

        var chatUsage = new ChatUsage(10, 10, 20);
        chatResponse = ChatResponse.build()
                .id("id")
                .modelId("modelId")
                .model("model")
                .modelVersion("modelVersion")
                .object("object")
                .usage(chatUsage)
                .createdAt("createdAt")
                .created(1L);
    }

    @Test
    void testWatsonxChatModelBuilder() {

        var chatModel = WatsonxChatModel.builder()
                .baseUrl(CloudRegion.FRANKFURT)
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
        assertNull(defaultRequestParameters.timeout());
        assertNull(defaultRequestParameters.toolChoice());
        assertNull(defaultRequestParameters.toolChoiceName());
        assertEquals(List.of(), defaultRequestParameters.toolSpecifications());
        assertNull(defaultRequestParameters.topK());
        assertNull(defaultRequestParameters.topLogprobs());
        assertNull(defaultRequestParameters.topP());
        assertNull(defaultRequestParameters.guidedChoice());
        assertNull(defaultRequestParameters.guidedGrammar());
        assertNull(defaultRequestParameters.guidedRegex());
        assertNull(defaultRequestParameters.repetitionPenalty());
        assertNull(defaultRequestParameters.lengthPenalty());

        assertDoesNotThrow(() -> WatsonxChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("model-name")
                .apiKey("api-key")
                .projectId("project-id")
                .spaceId("space-id")
                .build());
    }

    @Test
    void testDoChat() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            assertEquals("Hello", chatModel.chat("hello"));
            assertEquals(
                    List.of(UserMessage.text("hello")),
                    chatRequestCaptor.getValue().messages());
        });
    }

    @Test
    void testDoChatWithRefusal() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, "refusal", null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            assertThrows(ContentFilteredException.class, () -> chatModel.chat("hello"), "refusal");
        });
    }

    @Test
    void shouldExtractThinkingWhenConfiguredInModelBuilder() throws Exception {

        var extractionTags = ExtractionTags.of("think", "response");
        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE,
                "<think>I'm thinking</think><response>Hello</response>",
                "I'm thinking",
                null,
                null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        var cr = chatResponse.build();
        var field = ChatResponse.class.getDeclaredField("extractionTags");
        field.setAccessible(true);
        field.set(cr, extractionTags);

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(cr);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
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
                    chatRequestCaptor.getValue().messages().get(0));
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .thinking(ThinkingEffort.LOW)
                    .build();

            chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build());

            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .thinking(true)
                    .build();

            chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build());

            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .thinking(false)
                    .build();

            chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build());

            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertFalse(chatRequestCaptor.getValue().thinking().enabled());
        });
    }

    @Test
    void shouldExtractThinkingWhenConfiguredInRequestParameters() throws Exception {

        var extractionTags = ExtractionTags.of("think", "response");
        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE,
                "<think>I'm thinking</think><response>Hello</response>",
                "I'm thinking",
                null,
                null);

        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        var field = ChatResponse.class.getDeclaredField("extractionTags");
        var cr = chatResponse.build();
        field.setAccessible(true);
        field.set(cr, extractionTags);

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(cr);

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            var result = chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .thinking(extractionTags)
                            .build())
                    .build());
            assertEquals("Hello", result.aiMessage().text());
            assertEquals("I'm thinking", result.aiMessage().thinking());
            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .thinking(ThinkingEffort.LOW)
                            .build())
                    .build());
            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .thinking(true)
                            .build())
                    .build());
            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .thinking(false)
                            .build())
                    .build());
            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
            assertFalse(chatRequestCaptor.getValue().thinking().enabled());
        });
    }

    @Test
    void shouldThrowExceptionWhenThinkingUsedWithSystemMessage() {

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("ibm/granite-3-3-8b-instruct")
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
    }

    @Test
    void shouldThrowExceptionWhenThinkingUsedWithTools() {

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("ibm/granite-3-3-8b-instruct")
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
    }

    @Test
    void shouldReturnRawTextWhenThinkingIsNotEnabled() {

        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE,
                "<think>I'm thinking</think><response>Hello</response>",
                "I'm thinking",
                null,
                null);

        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .build();

            var result = chatModel.chat(ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build());
            assertEquals(
                    "<think>I'm thinking</think><response>Hello</response>",
                    result.aiMessage().text());
            assertNull(result.aiMessage().thinking());
            assertEquals(1, chatRequestCaptor.getValue().messages().size());
            assertEquals(
                    UserMessage.text("Hello"),
                    chatRequestCaptor.getValue().messages().get(0));
        });
    }

    @Test
    void testDoChatWithTool() {

        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, null, null, List.of(toolCall));
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
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

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("project-id")
                    .apiKey("api-key")
                    .listeners(List.of(new ChatModelListener() {}))
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .modelName("customModelName")
                            .frequencyPenalty(0.10)
                            .maxOutputTokens(10)
                            .presencePenalty(0.10)
                            .responseFormat(ResponseFormat.JSON)
                            .stopSequences(List.of("stop"))
                            .temperature(0.10)
                            .toolChoice(ToolChoice.REQUIRED)
                            .toolSpecifications(
                                    ToolSpecification.builder().name("name").build())
                            .topP(0.10)
                            .guidedChoice("a", "b")
                            .guidedGrammar("guidedGrammar")
                            .guidedRegex("guidedRegex")
                            .repetitionPenalty(1.1)
                            .lengthPenalty(1.2)
                            .build())
                    .build();

            chatModel.chat(chatRequest);
            assertEquals(
                    List.<ChatMessage>of(UserMessage.text("Hello")),
                    chatRequestCaptor.getValue().messages());

            var parameters = chatRequestCaptor.getValue().parameters();
            assertEquals(1, chatModel.listeners().size());
            assertNotNull(chatModel.listeners().get(0));
            assertEquals("customModelName", parameters.modelId());
            assertEquals(0.10, parameters.frequencyPenalty());
            assertEquals(10, parameters.maxCompletionTokens());
            assertEquals(0.10, parameters.presencePenalty());
            assertEquals("json_object", parameters.responseFormat());
            assertEquals(List.of("stop"), parameters.stop());
            assertEquals(0.10, parameters.temperature());
            assertEquals("required", parameters.toolChoiceOption());
            assertEquals(0.10, parameters.topP());
            assertEquals(Set.of("a", "b"), parameters.guidedChoice());
            assertEquals("guidedGrammar", parameters.guidedGrammar());
            assertEquals("guidedRegex", parameters.guidedRegex());
            assertEquals(1.1, parameters.repetitionPenalty());
            assertEquals(1.2, parameters.lengthPenalty());
        });
    }

    @Test
    void testChatRequestParameter() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
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
                    .timeout(Duration.ofMillis(30))
                    .topP(0.4)
                    .logitBias(Map.of("test", 10))
                    .logprobs(true)
                    .seed(5)
                    .toolChoiceName("toolChoiceName")
                    .topLogprobs(10)
                    .toolSpecifications(
                            ToolSpecification.builder().name("toolChoiceName").build())
                    .guidedChoice("a", "b")
                    .guidedGrammar("guidedGrammar")
                    .guidedRegex("guidedRegex")
                    .repetitionPenalty(1.1)
                    .lengthPenalty(1.2)
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().parameters();

            assertEquals(0.1, parameters.frequencyPenalty());
            assertEquals(0, parameters.maxCompletionTokens());
            assertEquals("modelName", parameters.modelId());
            assertEquals(0.2, parameters.presencePenalty());
            assertEquals(List.of("["), parameters.stop());
            assertEquals(0.3, parameters.temperature());
            assertEquals(null, parameters.toolChoiceOption());
            assertEquals(null, parameters.responseFormat());
            assertEquals(30, parameters.timeLimit());
            assertEquals(0.4, parameters.topP());
            assertEquals("projectId", parameters.projectId());
            assertEquals(Map.of("test", 10), parameters.logitBias());
            assertTrue(parameters.logprobs());
            assertEquals(5, parameters.seed());
            assertEquals("spaceId", parameters.spaceId());
            assertEquals(
                    Map.of("type", "function", "function", Map.of("name", "toolChoiceName")), parameters.toolChoice());
            assertEquals(10, parameters.topLogprobs());
            assertEquals(Set.of("a", "b"), parameters.guidedChoice());
            assertEquals("guidedGrammar", parameters.guidedGrammar());
            assertEquals("guidedRegex", parameters.guidedRegex());
            assertEquals(1.1, parameters.repetitionPenalty());
            assertEquals(1.2, parameters.lengthPenalty());
        });

        withChatServiceMock(() -> {
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
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
                            .timeout(Duration.ofMillis(30))
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
                            .guidedChoice("a", "b")
                            .guidedGrammar("guidedGrammar")
                            .guidedRegex("guidedRegex")
                            .repetitionPenalty(1.1)
                            .lengthPenalty(1.2)
                            .build())
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .toolSpecifications(
                            ToolSpecification.builder().name("toolChoiceName").build())
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().parameters();

            assertEquals(0.1, parameters.frequencyPenalty());
            assertEquals(0, parameters.maxCompletionTokens());
            assertEquals("default-model-name", parameters.modelId());
            assertEquals(0.2, parameters.presencePenalty());
            assertEquals(List.of("["), parameters.stop());
            assertEquals(0.3, parameters.temperature());
            assertEquals(null, parameters.toolChoiceOption());
            assertEquals(null, parameters.responseFormat());
            assertEquals(30, parameters.timeLimit());
            assertEquals(0.4, parameters.topP());
            assertEquals("default-project-id", parameters.projectId());
            assertEquals(Map.of("test", 10), parameters.logitBias());
            assertTrue(parameters.logprobs());
            assertEquals(5, parameters.seed());
            assertEquals("default-space-id", parameters.spaceId());
            assertEquals(
                    Map.of("type", "function", "function", Map.of("name", "toolChoiceName")), parameters.toolChoice());
            assertEquals(10, parameters.topLogprobs());
            assertNull(parameters.toolChoiceOption());
            assertEquals(Set.of("a", "b"), parameters.guidedChoice());
            assertEquals("guidedGrammar", parameters.guidedGrammar());
            assertEquals("guidedRegex", parameters.guidedRegex());
            assertEquals(1.1, parameters.repetitionPenalty());
            assertEquals(1.2, parameters.lengthPenalty());
        });

        withChatServiceMock(() -> {

            // TEST 1: Override paramaters
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
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
                            .timeout(Duration.ofMillis(30))
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
            var parameters = chatRequestCaptor.getValue().parameters();

            assertEquals("customModelName", parameters.modelId());
            assertEquals(0.10, parameters.frequencyPenalty());
            assertEquals(10, parameters.maxCompletionTokens());
            assertEquals(0.10, parameters.presencePenalty());
            assertEquals("json_object", parameters.responseFormat());
            assertEquals(List.of("stop"), parameters.stop());
            assertEquals(0.10, parameters.temperature());
            assertEquals(null, parameters.toolChoiceOption());
            assertEquals(0.10, parameters.topP());
            // ----------------
        });

        withChatServiceMock(() -> {

            // TEST 2: Override parameters
            var chatModel = WatsonxChatModel.builder()
                    .baseUrl("https://test.com")
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
                    .timeout(Duration.ofMillis(30))
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
                    .guidedChoice("defaultValue1", "defaultValue2")
                    .guidedGrammar("defaultGuidedGrammar")
                    .guidedRegex("defaultGuidedRegex")
                    .repetitionPenalty(1.0)
                    .lengthPenalty(1.0)
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
                            .timeout(Duration.ofMillis(40))
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
                            .guidedChoice("value1", "value2")
                            .guidedGrammar("guidedGrammar")
                            .guidedRegex("guidedRegex")
                            .repetitionPenalty(1.1)
                            .lengthPenalty(1.2)
                            .build())
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().parameters();

            assertEquals(0.2, parameters.frequencyPenalty());
            assertEquals(10, parameters.maxCompletionTokens());
            assertEquals("modelNames", parameters.modelId());
            assertEquals(0.3, parameters.presencePenalty());
            assertEquals(List.of("[]"), parameters.stop());
            assertEquals(0.4, parameters.temperature());
            assertEquals(null, parameters.toolChoiceOption());
            assertEquals("json_schema", parameters.responseFormat());
            assertNotNull(parameters.jsonSchema());
            assertEquals(40, parameters.timeLimit());
            assertEquals(0.5, parameters.topP());
            assertEquals("projectIds", parameters.projectId());
            assertEquals(Map.of("tests", 11), parameters.logitBias());
            assertFalse(parameters.logprobs());
            assertEquals(15, parameters.seed());
            assertEquals("spaceIds", parameters.spaceId());
            assertNotNull(parameters.toolChoice());
            assertEquals(11, parameters.topLogprobs());
            assertNull(parameters.toolChoiceOption());
            assertEquals(Set.of("value1", "value2"), parameters.guidedChoice());
            assertEquals("guidedGrammar", parameters.guidedGrammar());
            assertEquals("guidedRegex", parameters.guidedRegex());
            assertEquals(1.1, parameters.repetitionPenalty());
            assertEquals(1.2, parameters.lengthPenalty());
            // ----------------
        });
    }

    @Test
    void testChatRequestWithTopK() {

        var chatModel = WatsonxChatModel.builder()
                .baseUrl("https://test.com")
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
                .baseUrl("https://test.com")
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
                .baseUrl("https://test.com")
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
