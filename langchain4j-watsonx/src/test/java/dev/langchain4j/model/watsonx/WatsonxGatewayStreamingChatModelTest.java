package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.CompletedToolCall;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatRequest;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayChatResponse;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
public class WatsonxGatewayStreamingChatModelTest {

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
    void should_create_a_watsonx_gateway_streaming_chat_model() {

        var streamingChatModel = assertDoesNotThrow(() -> WatsonxGatewayStreamingChatModel.builder()
                .baseUrl(CloudRegion.FRANKFURT)
                .modelName("gpt-4o")
                .apiKey("api-key-test")
                .version("my-version")
                .logRequests(true)
                .logResponses(true)
                .build());

        var defaultRequestParameters = assertInstanceOf(
                WatsonxGatewayChatRequestParameters.class, streamingChatModel.defaultRequestParameters());

        var modelGatewayServiceField = assertDoesNotThrow(
                () -> streamingChatModel.getClass().getSuperclass().getDeclaredField("modelGatewayService"));
        var modelGatewayService = assertDoesNotThrow(() -> modelGatewayServiceField.get(streamingChatModel));

        assertInstanceOf(ModelGatewayService.class, modelGatewayService);
        assertEquals(ModelProvider.WATSONX, streamingChatModel.provider());
        assertEquals("gpt-4o", defaultRequestParameters.modelName());
        assertNull(defaultRequestParameters.serviceTier());
        assertNull(defaultRequestParameters.reasoningEffort());
        assertNull(defaultRequestParameters.topK());
    }

    @Test
    public void should_do_chat() throws Exception {

        var messages = List.<ChatMessage>of(com.ibm.watsonx.ai.chat.model.UserMessage.text("Hello"));
        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);

                    for (String response : List.of("Hello", "World")) handler.onPartialResponse(response, null);

                    var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello World", null, null, null);
                    var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
                    chatResponse.choices(List.of(resultChoice));
                    handler.onCompleteResponse(chatResponse.build());

                    return CompletableFuture.completedFuture(null);
                })
                .when(mockModelGatewayService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withModelGatewayServiceMock(() -> {
            var streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .build();

            var chatRequest =
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build();

            var receivedResponses = new ArrayList<>();
            var latch = new CountDownLatch(1);

            var streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    receivedResponses.add(partialResponse);
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    var metadata = (WatsonxChatResponseMetadata) completeResponse.metadata();
                    assertEquals("Hello World", completeResponse.aiMessage().text());
                    assertEquals("auto", metadata.getServiceTier());
                    assertEquals("fp", metadata.getSystemFingerprint());
                    assertEquals(true, metadata.getCached());
                    assertEquals("modelVersion", metadata.getModelVersion());
                    assertEquals(1L, metadata.getCreated());
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    fail("Unexpected error: " + error);
                }
            };

            streamingChatModel.chat(chatRequest, streamingHandler);
            assertEquals(messages, chatRequestCaptor.getValue().messages());
            var parameters = chatRequestCaptor.getValue().parameters();

            try {
                boolean completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
                assertEquals(List.of("Hello", "World"), receivedResponses);
                assertEquals("gpt-4o", parameters.modelId());
                assertNull(parameters.frequencyPenalty());
                assertNull(parameters.serviceTier());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_do_chat_with_refusal() {

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);

                    for (String response : List.of("Hello", "World")) handler.onPartialResponse(response, null);

                    var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello World", null, "refusal", null);
                    var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
                    chatResponse.choices(List.of(resultChoice));
                    handler.onCompleteResponse(chatResponse.build());

                    return CompletableFuture.completedFuture(null);
                })
                .when(mockModelGatewayService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withModelGatewayServiceMock(() -> {
            var streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .build();

            var chatRequest =
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build();

            var latch = new CountDownLatch(1);
            var streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {}

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    fail("onCompleteResponse must not be called after a refusal");
                }

                @Override
                public void onError(Throwable error) {
                    assertInstanceOf(ContentFilteredException.class, error);
                    assertEquals("refusal", error.getMessage());
                    latch.countDown();
                }
            };

            streamingChatModel.chat(chatRequest, streamingHandler);

            try {
                boolean completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    public void should_do_chat_with_tool() throws Exception {

        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, null, null, List.of(toolCall));
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "tool_calls");
        chatResponse.choices(List.of(resultChoice));

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onPartialToolCall(new com.ibm.watsonx.ai.chat.model.PartialToolCall(
                            "completion-id", 0, 0, null, "name", "{"));
                    handler.onPartialToolCall(new com.ibm.watsonx.ai.chat.model.PartialToolCall(
                            "completion-id", 0, 0, "id", "name", "}"));
                    handler.onCompleteToolCall(new CompletedToolCall("completion-id", 0, toolCall));
                    handler.onCompleteResponse(chatResponse.build());
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockModelGatewayService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withModelGatewayServiceMock(() -> {
            StreamingChatModel streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Hello"))
                    .toolSpecifications(ToolSpecification.builder()
                            .name("name")
                            .description("description")
                            .parameters(JsonObjectSchema.builder()
                                    .addStringProperty("string")
                                    .required("string")
                                    .build())
                            .build())
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            StreamingChatResponseHandler streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fail();
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    var metadata = (WatsonxChatResponseMetadata) completeResponse.metadata();
                    assertTrue(completeResponse.aiMessage().hasToolExecutionRequests());
                    assertEquals("id", completeResponse.id());
                    assertEquals("model", completeResponse.modelName());
                    assertEquals("modelVersion", metadata.getModelVersion());
                    assertEquals(1L, metadata.getCreated());
                    assertEquals("auto", metadata.getServiceTier());
                    assertEquals("fp", metadata.getSystemFingerprint());
                    assertEquals(true, metadata.getCached());
                    assertEquals(FinishReason.TOOL_EXECUTION, completeResponse.finishReason());
                    assertEquals(
                            1,
                            completeResponse.aiMessage().toolExecutionRequests().size());
                    assertEquals(
                            "name",
                            completeResponse
                                    .aiMessage()
                                    .toolExecutionRequests()
                                    .get(0)
                                    .name());
                    latch.countDown();
                }

                @Override
                public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                    assertEquals(0, completeToolCall.index());
                    assertEquals("id", completeToolCall.toolExecutionRequest().id());
                    assertEquals("name", completeToolCall.toolExecutionRequest().name());
                    assertEquals("{}", completeToolCall.toolExecutionRequest().arguments());
                }

                @Override
                public void onPartialToolCall(PartialToolCall partialToolCall) {
                    assertEquals(0, partialToolCall.index());
                    assertTrue(Objects.isNull(partialToolCall.id())
                            || partialToolCall.id().equals("id"));
                    assertEquals("name", partialToolCall.name());
                    assertTrue(partialToolCall.partialArguments().equals("{")
                            || partialToolCall.partialArguments().equals("}"));
                }

                @Override
                public void onError(Throwable error) {
                    fail("Unexpected error: " + error);
                }
            };

            streamingChatModel.chat(chatRequest, streamingHandler);

            try {
                boolean completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_handle_chat_request_parameters() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onCompleteResponse(chatResponse.build());
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockModelGatewayService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withModelGatewayServiceMock(() -> {
            var streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .defaultRequestParameters(WatsonxGatewayChatRequestParameters.builder()
                            .serviceTier(ServiceTier.FLEX)
                            .reasoningEffort(ReasoningEffort.HIGH)
                            .toolChoice(ToolChoice.REQUIRED)
                            .toolSpecifications(
                                    ToolSpecification.builder().name("test").build())
                            .build())
                    .build();

            var latch = new CountDownLatch(1);
            var streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {}

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    fail("Unexpected error: " + error);
                }
            };

            streamingChatModel.chat("Hello", streamingHandler);
            var parameters = chatRequestCaptor.getValue().parameters();

            try {
                var completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
                assertEquals("flex", parameters.serviceTier());
                assertEquals("high", parameters.reasoningEffort());
                assertEquals("required", parameters.toolChoiceOption());
                assertNotNull(parameters.modelId());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_propagate_error_through_exception_mapper() {

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onError(new Exception("test"));
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockModelGatewayService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withModelGatewayServiceMock(() -> {
            var streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-4o")
                    .apiKey("api-key")
                    .build();

            var latch = new CountDownLatch(1);
            var streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {}

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {}

                @Override
                public void onError(Throwable error) {
                    assertEquals("test", error.getMessage());
                    latch.countDown();
                }
            };

            streamingChatModel.chat("Hello", streamingHandler);

            try {
                var completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_throw_exception_for_chat_request_with_top_k() {

        var streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .build();

        assertThrows(
                UnsupportedFeatureException.class,
                () -> streamingChatModel.chat(
                        ChatRequest.builder()
                                .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                                .topK(10)
                                .build(),
                        new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String partialResponse) {}

                            @Override
                            public void onCompleteResponse(
                                    dev.langchain4j.model.chat.response.ChatResponse completeResponse) {}

                            @Override
                            public void onError(Throwable error) {}
                        }));

        assertThrows(
                UnsupportedFeatureException.class,
                () -> WatsonxGatewayStreamingChatModel.builder()
                        .baseUrl("https://test.com")
                        .modelName("gpt-4o")
                        .apiKey("api-key")
                        .defaultRequestParameters(
                                ChatRequestParameters.builder().topK(10).build())
                        .build());
    }

    @Test
    void should_support_capabilities() {

        var streamingChatModel = WatsonxGatewayStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("gpt-4o")
                .apiKey("api-key")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertEquals(1, streamingChatModel.supportedCapabilities().size());
        assertTrue(streamingChatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
    }

    private void withModelGatewayServiceMock(Runnable action) {
        try (MockedStatic<ModelGatewayService> mockedStatic = mockStatic(ModelGatewayService.class)) {
            mockedStatic.when(ModelGatewayService::builder).thenReturn(mockModelGatewayServiceBuilder);
            action.run();
        }
    }
}
