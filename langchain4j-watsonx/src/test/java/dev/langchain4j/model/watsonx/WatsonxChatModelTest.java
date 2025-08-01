package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.FinishReason;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WatsonxChatModelTest {

    @Mock
    ChatService mockChatService;

    @Captor
    ArgumentCaptor<ChatParameters> chatParameterCaptor;

    static ChatResponse chatResponse;

    @BeforeAll
    static void setUp() {
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
    public void testDoChat() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(eq(List.of(UserMessage.text("hello"))), eq(null), any()))
                .thenReturn(chatResponse);

        ChatModel chatModel =
                WatsonxChatModel.builder().service(mockChatService).build();

        assertEquals("Hello", chatModel.chat("hello"));
    }

    @Test
    public void testDoChatWithTool() {

        var messages = List.<ChatMessage>of(UserMessage.text("hello"));
        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, "refusal", List.of(toolCall));
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));
        when(mockChatService.chat(eq(messages), any(), any())).thenReturn(chatResponse);

        ChatModel chatModel =
                WatsonxChatModel.builder().service(mockChatService).build();

        ChatRequest chatRequest = ChatRequest.builder()
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
        assertEquals("model", metadata.getModel());
        assertEquals("modelVersion", metadata.getModelVersion());
        assertEquals("object", metadata.getObject());
        assertEquals("createdAt", metadata.getCreatedAt());
        assertEquals(1L, metadata.getCreated());
        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(10, response.tokenUsage().inputTokenCount());
        assertEquals(10, response.tokenUsage().outputTokenCount());
        assertEquals(20, response.tokenUsage().totalTokenCount());
        assertTrue(response.aiMessage().hasToolExecutionRequests());
        assertEquals(1, response.aiMessage().toolExecutionRequests().size());
        assertEquals("name", response.aiMessage().toolExecutionRequests().get(0).name());
        assertEquals("id", response.aiMessage().toolExecutionRequests().get(0).id());
        assertEquals("{}", response.aiMessage().toolExecutionRequests().get(0).arguments());
    }

    @Test
    void testChatRequest() {

        var messages = List.<ChatMessage>of(UserMessage.text("Hello"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(eq(messages), any(), chatParameterCaptor.capture()))
                .thenReturn(chatResponse);

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

        ChatModel chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .listeners(List.of(new ChatModelListener() {}))
                .build();

        chatModel.chat(chatRequest);

        assertEquals(1, chatModel.listeners().size());
        assertNotNull(chatModel.listeners().get(0));
        assertEquals("customModelName", chatParameterCaptor.getValue().getModelId());
        assertEquals(0.10, chatParameterCaptor.getValue().getFrequencyPenalty());
        assertEquals(10, chatParameterCaptor.getValue().getMaxCompletionTokens());
        assertEquals(0.10, chatParameterCaptor.getValue().getPresencePenalty());
        assertEquals("json_object", chatParameterCaptor.getValue().getResponseFormat());
        assertEquals(List.of("stop"), chatParameterCaptor.getValue().getStop());
        assertEquals(0.10, chatParameterCaptor.getValue().getTemperature());
        assertEquals("required", chatParameterCaptor.getValue().getToolChoiceOption());
        assertEquals(0.10, chatParameterCaptor.getValue().getTopP());
    }

    @Test
    void testChatRequestParameter() {

        var messages = List.<ChatMessage>of(UserMessage.text("Hello"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(eq(messages), any(), chatParameterCaptor.capture()))
                .thenReturn(chatResponse);

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
                        .build())
                .build();

        ChatModel chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .listeners(List.of(new ChatModelListener() {}))
                .build();

        chatModel.chat(chatRequest);

        assertEquals(1, chatModel.listeners().size());
        assertNotNull(chatModel.listeners().get(0));
        assertEquals("customModelName", chatParameterCaptor.getValue().getModelId());
        assertEquals(0.10, chatParameterCaptor.getValue().getFrequencyPenalty());
        assertEquals(10, chatParameterCaptor.getValue().getMaxCompletionTokens());
        assertEquals(0.10, chatParameterCaptor.getValue().getPresencePenalty());
        assertEquals("json_object", chatParameterCaptor.getValue().getResponseFormat());
        assertEquals(List.of("stop"), chatParameterCaptor.getValue().getStop());
        assertEquals(0.10, chatParameterCaptor.getValue().getTemperature());
        assertEquals("required", chatParameterCaptor.getValue().getToolChoiceOption());
        assertEquals(0.10, chatParameterCaptor.getValue().getTopP());
    }

    @Test
    void testChatRequestParameterOverride() {

        var messages = List.<ChatMessage>of(UserMessage.text("Hello"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        var chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .defaultRequestParameters(WatsonxChatRequestParameters.builder()
                        .frequencyPenalty(0.1)
                        .maxOutputTokens(0)
                        .modelName("modelName")
                        .presencePenalty(0.2)
                        .stopSequences("[")
                        .temperature(0.3)
                        .toolChoice(ToolChoice.REQUIRED)
                        .responseFormat(ResponseFormat.TEXT)
                        .timeLimit(Duration.ofMillis(30))
                        .topK(1)
                        .topP(0.4)
                        .projectId("projectId")
                        .logitBias(Map.of("test", 10))
                        .logprobs(true)
                        .seed(5)
                        .spaceId("spaceId")
                        .toolChoiceName("toolChoiceName")
                        .toolSpecifications(
                                ToolSpecification.builder().name("test").build())
                        .topLogprobs(10)
                        .build())
                .build();

        when(mockChatService.chat(eq(messages), any(), chatParameterCaptor.capture()))
                .thenReturn(chatResponse);

        var chatRequest = ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                .toolSpecifications(
                        ToolSpecification.builder().name("toolChoiceName").build())
                .build();

        chatModel.chat(chatRequest);

        assertEquals(0.1, chatParameterCaptor.getValue().getFrequencyPenalty());
        assertEquals(0, chatParameterCaptor.getValue().getMaxCompletionTokens());
        assertEquals("modelName", chatParameterCaptor.getValue().getModelId());
        assertEquals(0.2, chatParameterCaptor.getValue().getPresencePenalty());
        assertEquals(List.of("["), chatParameterCaptor.getValue().getStop());
        assertEquals(0.3, chatParameterCaptor.getValue().getTemperature());
        assertEquals(null, chatParameterCaptor.getValue().getToolChoiceOption());
        assertEquals(null, chatParameterCaptor.getValue().getResponseFormat());
        assertEquals(30, chatParameterCaptor.getValue().getTimeLimit());
        assertEquals(0.4, chatParameterCaptor.getValue().getTopP());
        assertEquals("projectId", chatParameterCaptor.getValue().getProjectId());
        assertEquals(Map.of("test", 10), chatParameterCaptor.getValue().getLogitBias());
        assertTrue(chatParameterCaptor.getValue().getLogprobs());
        assertEquals(5, chatParameterCaptor.getValue().getSeed());
        assertEquals("spaceId", chatParameterCaptor.getValue().getSpaceId());
        assertEquals(
                Map.of("type", "function", "function", Map.of("name", "toolChoiceName")),
                chatParameterCaptor.getValue().getToolChoice());
        assertEquals(10, chatParameterCaptor.getValue().getTopLogprobs());
        assertNull(chatParameterCaptor.getValue().getToolChoiceOption());

        // TEST 1: Override paramaters
        chatRequest = ChatRequest.builder()
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

        assertEquals("customModelName", chatParameterCaptor.getValue().getModelId());
        assertEquals(0.10, chatParameterCaptor.getValue().getFrequencyPenalty());
        assertEquals(10, chatParameterCaptor.getValue().getMaxCompletionTokens());
        assertEquals(0.10, chatParameterCaptor.getValue().getPresencePenalty());
        assertEquals("json_object", chatParameterCaptor.getValue().getResponseFormat());
        assertEquals(List.of("stop"), chatParameterCaptor.getValue().getStop());
        assertEquals(0.10, chatParameterCaptor.getValue().getTemperature());
        assertEquals(null, chatParameterCaptor.getValue().getToolChoiceOption());
        assertEquals(0.10, chatParameterCaptor.getValue().getTopP());
        // ----------------

        // TEST 2: Override parameters
        chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .enableJsonSchema(true)
                .defaultRequestParameters(WatsonxChatRequestParameters.builder()
                        .frequencyPenalty(0.1)
                        .maxOutputTokens(0)
                        .modelName("modelName")
                        .presencePenalty(0.2)
                        .stopSequences("[")
                        .temperature(0.3)
                        .toolChoice(ToolChoice.REQUIRED)
                        .responseFormat(ResponseFormat.TEXT)
                        .timeLimit(Duration.ofMillis(30))
                        .topK(1)
                        .topP(0.4)
                        .projectId("projectId")
                        .logitBias(Map.of("test", 10))
                        .logprobs(true)
                        .seed(5)
                        .spaceId("spaceId")
                        .toolChoiceName("toolChoiceName")
                        .toolSpecifications(
                                ToolSpecification.builder().name("test").build())
                        .topLogprobs(10)
                        .build())
                .build();

        chatRequest = ChatRequest.builder()
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
                        .topK(1)
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

        assertEquals(0.2, chatParameterCaptor.getValue().getFrequencyPenalty());
        assertEquals(10, chatParameterCaptor.getValue().getMaxCompletionTokens());
        assertEquals("modelNames", chatParameterCaptor.getValue().getModelId());
        assertEquals(0.3, chatParameterCaptor.getValue().getPresencePenalty());
        assertEquals(List.of("[]"), chatParameterCaptor.getValue().getStop());
        assertEquals(0.4, chatParameterCaptor.getValue().getTemperature());
        assertEquals(null, chatParameterCaptor.getValue().getToolChoiceOption());
        assertEquals("json_schema", chatParameterCaptor.getValue().getResponseFormat());
        assertNotNull(chatParameterCaptor.getValue().getJsonSchema());
        assertEquals(40, chatParameterCaptor.getValue().getTimeLimit());
        assertEquals(0.5, chatParameterCaptor.getValue().getTopP());
        assertEquals("projectIds", chatParameterCaptor.getValue().getProjectId());
        assertEquals(Map.of("tests", 11), chatParameterCaptor.getValue().getLogitBias());
        assertFalse(chatParameterCaptor.getValue().getLogprobs());
        assertEquals(15, chatParameterCaptor.getValue().getSeed());
        assertEquals("spaceIds", chatParameterCaptor.getValue().getSpaceId());
        assertNotNull(chatParameterCaptor.getValue().getToolChoice());
        assertEquals(11, chatParameterCaptor.getValue().getTopLogprobs());
        assertNull(chatParameterCaptor.getValue().getToolChoiceOption());
        assertEquals(1, chatModel.supportedCapabilities().size());
        assertTrue(chatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
        // ----------------
    }

    @Test
    void testSupportCapabilities() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(any(), any(), chatParameterCaptor.capture())).thenReturn(chatResponse);

        var chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .enableJsonSchema(false)
                .defaultRequestParameters(WatsonxChatRequestParameters.builder()
                        .modelName("modelName")
                        .toolChoice(ToolChoice.REQUIRED)
                        .responseFormat(JsonSchema.builder()
                                .name("test")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("city")
                                        .build())
                                .build())
                        .toolSpecifications(
                                ToolSpecification.builder().name("test").build())
                        .build())
                .build();

        chatModel.chat("Hello");
        assertEquals("json_schema", chatParameterCaptor.getValue().getResponseFormat());
        assertNotNull(chatParameterCaptor.getValue().getJsonSchema());
        assertEquals(1, chatModel.supportedCapabilities().size());
        assertTrue(chatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
        assertEquals("required", chatParameterCaptor.getValue().getToolChoiceOption());
    }
}
