package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
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
    ArgumentCaptor<com.ibm.watsonx.ai.chat.ChatRequest> chatRequestCaptor;

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

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        ChatModel chatModel =
                WatsonxChatModel.builder().service(mockChatService).build();

        assertEquals("Hello", chatModel.chat("hello"));
        assertEquals(
                List.of(UserMessage.text("hello")), chatRequestCaptor.getValue().getMessages());
    }

    @Test
    public void testDoChatWithTags() {

        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE, "<think>I'm thinking</think><response>Hello</response>", "refusal", null);

        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(any(com.ibm.watsonx.ai.chat.ChatRequest.class)))
                .thenReturn(chatResponse);

        ChatModel chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .thinking(ExtractionTags.of("think", "response"))
                .build();

        assertEquals("Hello", chatModel.chat("hello"));
        var result = chatModel.chat(ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                .build());
        assertEquals("Hello", result.aiMessage().text());
        assertEquals("I'm thinking", result.aiMessage().thinking());

        resultMessage = new ResultMessage(AssistantMessage.ROLE, "<think>I'm thinking</think>Hello", "refusal", null);

        resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(any(com.ibm.watsonx.ai.chat.ChatRequest.class)))
                .thenReturn(chatResponse);

        chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
                .thinking(new ExtractionTags("think"))
                .build();

        result = chatModel.chat(ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                .build());

        assertEquals("Hello", result.aiMessage().text());
        assertEquals("I'm thinking", result.aiMessage().thinking());
    }

    @Test
    public void testDoChatWithTool() {

        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, "refusal", List.of(toolCall));
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));
        when(mockChatService.chat(any(com.ibm.watsonx.ai.chat.ChatRequest.class)))
                .thenReturn(chatResponse);

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
        assertEquals("modelVersion", metadata.getModelVersion());
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

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

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
    }

    @Test
    void testChatRequestParameter() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

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
    }

    @Test
    void testChatRequestParameterOverride() {

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
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        var chatRequest = ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                .toolSpecifications(
                        ToolSpecification.builder().name("toolChoiceName").build())
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
                Map.of("type", "function", "function", Map.of("name", "toolChoiceName")), parameters.getToolChoice());
        assertEquals(10, parameters.getTopLogprobs());
        assertNull(parameters.getToolChoiceOption());

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
        parameters = chatRequestCaptor.getValue().getParameters();

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

        // TEST 2: Override parameters
        chatModel = WatsonxChatModel.builder()
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
        parameters = chatRequestCaptor.getValue().getParameters();

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
    }

    @Test
    void testSupportCapabilities() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", "refusal", null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        when(mockChatService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);

        var chatModel = WatsonxChatModel.builder()
                .service(mockChatService)
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
        var parameters = chatRequestCaptor.getValue().getParameters();

        assertEquals("json_schema", parameters.getResponseFormat());
        assertNotNull(parameters.getJsonSchema());
        assertEquals(1, chatModel.supportedCapabilities().size());
        assertTrue(chatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
        assertEquals("required", parameters.getToolChoiceOption());
    }
}
