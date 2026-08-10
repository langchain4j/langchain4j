package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatRequest;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatResponse;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Cache;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Router;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
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
public class WatsonxGatewayChatModelTest {

    @Mock
    ModelGatewayService mockModelGatewayService;

    @Mock
    ModelGatewayService.Builder mockModelGatewayServiceBuilder;

    @Captor
    ArgumentCaptor<ModelGatewayChatRequest> chatRequestCaptor;

    static ModelGatewayChatResponse.Builder<?> chatResponse;

    @BeforeEach
    void setUp() {

        when(mockModelGatewayServiceBuilder.modelId(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.baseUrl(any(URI.class))).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.timeout(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.version(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.logRequests(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.logResponses(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.authenticator(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.apiKey(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.httpClient(any())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.verifySsl(anyBoolean())).thenReturn(mockModelGatewayServiceBuilder);
        when(mockModelGatewayServiceBuilder.build()).thenReturn(mockModelGatewayService);

        var chatUsage = new ChatUsage(10, 10, 20);
        chatResponse = ModelGatewayChatResponse.builder()
                .id("id")
                .modelId("modelId")
                .model("model")
                .modelVersion("modelVersion")
                .object("object")
                .usage(chatUsage)
                .createdAt("createdAt")
                .created(1L)
                .serviceTier("auto")
                .systemFingerprint("fp")
                .cached(true);
    }

    @Test
    void should_create_a_watsonx_gateway_chat_model() {

        var chatModel = assertDoesNotThrow(() -> WatsonxGatewayChatModel.builder()
                .baseUrl(CloudRegion.FRANKFURT)
                .modelName("gpt-4o")
                .apiKey("api-key-test")
                .version("my-version")
                .logRequests(true)
                .logResponses(true)
                .build());

        var defaultRequestParameters =
                assertInstanceOf(WatsonxGatewayChatRequestParameters.class, chatModel.defaultRequestParameters());

        var modelGatewayServiceField =
                assertDoesNotThrow(() -> chatModel.getClass().getSuperclass().getDeclaredField("modelGatewayService"));
        var modelGatewayService = assertDoesNotThrow(() -> modelGatewayServiceField.get(chatModel));

        assertInstanceOf(ModelGatewayService.class, modelGatewayService);
        assertEquals(ModelProvider.WATSONX, chatModel.provider());
        assertNull(defaultRequestParameters.frequencyPenalty());
        assertNull(defaultRequestParameters.logitBias());
        assertNull(defaultRequestParameters.logprobs());
        assertNull(defaultRequestParameters.maxOutputTokens());
        assertEquals("gpt-4o", defaultRequestParameters.modelName());
        assertNull(defaultRequestParameters.presencePenalty());
        assertNull(defaultRequestParameters.responseFormat());
        assertNull(defaultRequestParameters.seed());
        assertEquals(List.of(), defaultRequestParameters.stopSequences());
        assertNull(defaultRequestParameters.temperature());
        assertNull(defaultRequestParameters.timeout());
        assertNull(defaultRequestParameters.toolChoice());
        assertNull(defaultRequestParameters.toolChoiceName());
        assertEquals(List.of(), defaultRequestParameters.toolSpecifications());
        assertNull(defaultRequestParameters.topK());
        assertNull(defaultRequestParameters.topLogprobs());
        assertNull(defaultRequestParameters.topP());
        assertNull(defaultRequestParameters.serviceTier());
        assertNull(defaultRequestParameters.reasoningEffort());
        assertNull(defaultRequestParameters.router());
        assertNull(defaultRequestParameters.modalities());
        assertNull(defaultRequestParameters.store());
        assertNull(defaultRequestParameters.parallelToolCalls());
        assertNull(defaultRequestParameters.user());
        assertNull(defaultRequestParameters.metadata());
    }

    @Test
    void should_do_chat() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .build();

            assertEquals("Hello", chatModel.chat("hello"));
            assertEquals(
                    List.of(UserMessage.text("hello")),
                    chatRequestCaptor.getValue().messages());
        });
    }

    @Test
    void should_do_chat_with_refusal() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, "refusal", null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .build();

            assertThrows(ContentFilteredException.class, () -> chatModel.chat("hello"), "refusal");
        });
    }

    @Test
    void should_do_chat_with_tool() {

        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, null, null, List.of(toolCall));
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
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
            assertEquals("model", response.modelName());
            assertEquals("modelVersion", metadata.getModelVersion());
            assertEquals(1L, metadata.getCreated());
            assertEquals("auto", metadata.getServiceTier());
            assertEquals("fp", metadata.getSystemFingerprint());
            assertEquals(true, metadata.getCached());
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
    void should_handle_chat_request() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .listeners(List.of(new ChatModelListener() {}))
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxGatewayChatRequestParameters.builder()
                            .modelName("claude-3-5-sonnet")
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
                            .serviceTier(ServiceTier.FLEX)
                            .reasoningEffort(ReasoningEffort.HIGH)
                            .modalities(List.of("text"))
                            .store(true)
                            .parallelToolCalls(true)
                            .user("user")
                            .metadata(Map.of("k", "v"))
                            .logitBias(Map.of("token", 1))
                            .logprobs(true)
                            .topLogprobs(5)
                            .seed(42)
                            .build())
                    .build();

            chatModel.chat(chatRequest);
            assertEquals(
                    List.<ChatMessage>of(UserMessage.text("Hello")),
                    chatRequestCaptor.getValue().messages());

            var parameters = chatRequestCaptor.getValue().parameters();
            assertEquals(1, chatModel.listeners().size());
            assertEquals("claude-3-5-sonnet", parameters.modelId());
            assertEquals(0.10, parameters.frequencyPenalty());
            assertEquals(10, parameters.maxCompletionTokens());
            assertEquals(0.10, parameters.presencePenalty());
            assertEquals("json_object", parameters.responseFormat());
            assertEquals(List.of("stop"), parameters.stop());
            assertEquals(0.10, parameters.temperature());
            assertEquals("required", parameters.toolChoiceOption());
            assertEquals(0.10, parameters.topP());
            assertEquals("flex", parameters.serviceTier());
            assertEquals("high", parameters.reasoningEffort());
            assertEquals(List.of("text"), parameters.modalities());
            assertEquals(true, parameters.store());
            assertEquals(true, parameters.parallelToolCalls());
            assertEquals("user", parameters.user());
            assertEquals(Map.of("k", "v"), parameters.metadata());
            assertEquals(Map.of("token", 1), parameters.logitBias());
            assertTrue(parameters.logprobs());
            assertEquals(5, parameters.topLogprobs());
            assertEquals(42, parameters.seed());
        });
    }

    @Test
    void should_handle_chat_request_parameters_from_builder() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
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
                    .serviceTier(ServiceTier.AUTO)
                    .reasoningEffort(ReasoningEffort.LOW)
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
            var parameters = chatRequestCaptor.getValue().parameters();

            assertEquals(0.1, parameters.frequencyPenalty());
            assertEquals(0, parameters.maxCompletionTokens());
            assertEquals("gpt-4o", parameters.modelId());
            assertEquals(0.2, parameters.presencePenalty());
            assertEquals(List.of("["), parameters.stop());
            assertEquals(0.3, parameters.temperature());
            assertNull(parameters.toolChoiceOption());
            assertNull(parameters.responseFormat());
            assertEquals(30, parameters.timeLimit());
            assertEquals(0.4, parameters.topP());
            assertEquals("auto", parameters.serviceTier());
            assertEquals("low", parameters.reasoningEffort());
            assertEquals(Map.of("test", 10), parameters.logitBias());
            assertTrue(parameters.logprobs());
            assertEquals(5, parameters.seed());
            assertEquals(
                    Map.of("type", "function", "function", Map.of("name", "toolChoiceName")), parameters.toolChoice());
            assertEquals(10, parameters.topLogprobs());
        });
    }

    @Test
    void should_override_default_parameters_with_request_parameters() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .apiKey("api-key")
                    .defaultRequestParameters(WatsonxGatewayChatRequestParameters.builder()
                            .modelName("default-model-name")
                            .frequencyPenalty(0.1)
                            .maxOutputTokens(0)
                            .presencePenalty(0.2)
                            .stopSequences("[")
                            .temperature(0.3)
                            .topP(0.4)
                            .serviceTier(ServiceTier.AUTO)
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
            assertEquals(0.10, parameters.topP());
            // the default service tier survives because the request did not override it
            assertEquals("auto", parameters.serviceTier());
        });
    }

    @Test
    void should_use_json_schema_response_format() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                    .build();

            var chatRequest = ChatRequest.builder()
                    .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                    .parameters(WatsonxGatewayChatRequestParameters.builder()
                            .responseFormat(JsonSchema.builder()
                                    .name("test")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("city")
                                            .build())
                                    .build())
                            .build())
                    .build();

            chatModel.chat(chatRequest);
            var parameters = chatRequestCaptor.getValue().parameters();

            assertEquals("json_schema", parameters.responseFormat());
            assertEquals("test", parameters.jsonSchema().name());
        });
    }

    @Test
    void should_throw_exception_for_chat_request_with_top_k() {

        var chatModel = WatsonxGatewayChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .build();

        assertThrows(
                UnsupportedFeatureException.class,
                () -> chatModel.chat(ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                        .topK(10)
                        .build()));

        assertThrows(
                UnsupportedFeatureException.class,
                () -> WatsonxGatewayChatModel.builder()
                        .baseUrl("https://test.com")
                        .modelName("gpt-4o")
                        .apiKey("api-key")
                        .defaultRequestParameters(
                                ChatRequestParameters.builder().topK(10).build())
                        .build());
    }

    @Test
    void should_support_capabilities() {

        var chatModel = WatsonxGatewayChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertEquals(1, chatModel.supportedCapabilities().size());
        assertTrue(chatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
    }

    @Test
    void should_merge_every_gateway_builder_setter_into_the_default_parameters() {

        var router = new Router(new Cache(true, null, null));

        var chatModel = WatsonxGatewayChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .serviceTier(ServiceTier.AUTO)
                .reasoningEffort(ReasoningEffort.LOW)
                .router(router)
                .modalities(List.of("text"))
                .store(true)
                .parallelToolCalls(true)
                .user("user")
                .metadata(Map.of("key", "value"))
                .build();

        var parameters =
                assertInstanceOf(WatsonxGatewayChatRequestParameters.class, chatModel.defaultRequestParameters());

        assertEquals(ServiceTier.AUTO, parameters.serviceTier());
        assertEquals(ReasoningEffort.LOW, parameters.reasoningEffort());
        assertEquals(router, parameters.router());
        assertEquals(List.of("text"), parameters.modalities());
        assertTrue(parameters.store());
        assertTrue(parameters.parallelToolCalls());
        assertEquals("user", parameters.user());
        assertEquals(Map.of("key", "value"), parameters.metadata());
    }

    @Test
    void cache_should_be_a_shortcut_for_router() {

        var cache = new Cache(true, null, null);

        var withCache = WatsonxGatewayChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .cache(cache)
                .build();

        assertEquals(
                new Router(cache),
                ((WatsonxGatewayChatRequestParameters) withCache.defaultRequestParameters()).router());

        var withoutCache = WatsonxGatewayChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .cache(null)
                .build();

        assertNull(((WatsonxGatewayChatRequestParameters) withoutCache.defaultRequestParameters()).router());
    }

    @Test
    void should_authenticate_with_an_authenticator_instead_of_an_api_key() {

        var authenticator = mock(IBMCloudAuthenticator.class);

        withModelGatewayServiceMock(() -> {
            assertDoesNotThrow(() -> WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .authenticator(authenticator)
                    .build());

            verify(mockModelGatewayServiceBuilder).authenticator(authenticator);
            verify(mockModelGatewayServiceBuilder, never()).apiKey(any());
        });
    }

    @Test
    void should_do_chat_with_strict_json_schema() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockModelGatewayService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        var responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("content")
                                .addBooleanProperty("flag")
                                .required("content")
                                .build())
                        .build())
                .build();

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .responseFormat(responseFormat)
                    .build();

            chatModel.chat("hello");
            var jsonSchema = chatRequestCaptor.getValue().parameters().jsonSchema();
            var schema = assertInstanceOf(Map.class, jsonSchema.schema());

            assertTrue(jsonSchema.strict());
            assertEquals(List.of("content", "flag"), schema.get("required"));
            assertEquals(false, schema.get("additionalProperties"));
        });

        withModelGatewayServiceMock(() -> {
            var chatModel = WatsonxGatewayChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .responseFormat(responseFormat)
                    .strictJsonSchema(false)
                    .build();

            chatModel.chat("hello");
            var jsonSchema = chatRequestCaptor.getValue().parameters().jsonSchema();
            var schema = assertInstanceOf(Map.class, jsonSchema.schema());

            assertFalse(jsonSchema.strict());
            assertEquals(List.of("content"), schema.get("required"));
            assertFalse(schema.containsKey("additionalProperties"));
        });
    }

    private void withModelGatewayServiceMock(Runnable action) {
        try (MockedStatic<ModelGatewayService> mockedStatic = mockStatic(ModelGatewayService.class)) {
            mockedStatic.when(ModelGatewayService::builder).thenReturn(mockModelGatewayServiceBuilder);
            action.run();
        }
    }
}
