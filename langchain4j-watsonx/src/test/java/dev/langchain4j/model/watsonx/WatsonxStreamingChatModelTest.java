package dev.langchain4j.model.watsonx;

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

import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.CompletedToolCall;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
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
public class WatsonxStreamingChatModelTest {

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
    public void testDoChat() throws Exception {

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
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withChatServiceMock(() -> {
            var streamingChatModel = WatsonxStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("projectId")
                    .spaceId("spaceId")
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
                    assertEquals("Hello World", completeResponse.aiMessage().text());
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
                assertNull(parameters.frequencyPenalty());
                assertNull(parameters.jsonSchema());
                assertNull(parameters.logitBias());
                assertNull(parameters.logprobs());
                assertNull(parameters.maxCompletionTokens());
                assertEquals("modelId", parameters.modelId());
                assertNull(parameters.n());
                assertNull(parameters.presencePenalty());
                assertEquals("projectId", parameters.projectId());
                assertNull(parameters.responseFormat());
                assertNull(parameters.seed());
                assertEquals("spaceId", parameters.spaceId());
                assertEquals(List.of(), parameters.stop());
                assertNull(parameters.temperature());
                assertNull(parameters.timeLimit());
                assertNull(parameters.toolChoice());
                assertNull(parameters.toolChoiceOption());
                assertNull(parameters.topLogprobs());
                assertNull(parameters.topP());
                assertNull(parameters.guidedChoice());
                assertNull(parameters.guidedGrammar());
                assertNull(parameters.guidedRegex());
                assertNull(parameters.repetitionPenalty());
                assertNull(parameters.lengthPenalty());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void shouldExtractThinkingWhenConfiguredInModelBuilder() throws Exception {

        var extractionTags = ExtractionTags.of("think", "response");

        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE,
                "<think>I'm thinking</think><response>This is the response</response>",
                "I'm thinking",
                null,
                null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        var cr = chatResponse.build();
        var field = ChatResponse.class.getDeclaredField("extractionTags");
        field.setAccessible(true);
        field.set(cr, extractionTags);

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onPartialThinking("I'm thinking", null);
                    handler.onPartialResponse("This is", null);
                    handler.onPartialResponse("the response", null);
                    handler.onCompleteResponse(cr);
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withChatServiceMock(() -> {
            StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("projectId")
                    .spaceId("spaceId")
                    .apiKey("api-key")
                    .thinking(extractionTags)
                    .build();

            ChatRequest chatRequest =
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build();

            CountDownLatch latch = new CountDownLatch(1);
            StreamingChatResponseHandler streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    assertTrue(partialResponse.equals("This is") || partialResponse.equals("the response"));
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    assertEquals("I'm thinking", completeResponse.aiMessage().thinking());
                    assertEquals(
                            "This is the response", completeResponse.aiMessage().text());
                    latch.countDown();
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking) {
                    assertEquals("I'm thinking", partialThinking.text());
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
                assertEquals(
                        com.ibm.watsonx.ai.chat.model.UserMessage.text("Hello"),
                        chatRequestCaptor.getValue().messages().get(0));
                assertNotNull(chatRequestCaptor.getValue().thinking());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void shouldExtractThinkingWhenConfiguredInRequestParameters() throws Exception {

        var extractionTags = ExtractionTags.of("think", "response");
        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE,
                "<think>I'm thinking</think><response>This is the response</response>",
                "I'm thinking",
                null,
                null);

        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));
        var cr = chatResponse.build();
        var field = ChatResponse.class.getDeclaredField("extractionTags");
        field.setAccessible(true);
        field.set(cr, extractionTags);

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onPartialThinking("I'm thinking", null);
                    handler.onPartialResponse("This is", null);
                    handler.onPartialResponse("the response", null);
                    handler.onCompleteResponse(cr);
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withChatServiceMock(() -> {
            StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("projectId")
                    .spaceId("spaceId")
                    .apiKey("api-key")
                    .build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Hello"))
                    .parameters(WatsonxChatRequestParameters.builder()
                            .thinking(ExtractionTags.of("think", "response"))
                            .build())
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            StreamingChatResponseHandler streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    assertTrue(partialResponse.equals("This is") || partialResponse.equals("the response"));
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    assertEquals("I'm thinking", completeResponse.aiMessage().thinking());
                    assertEquals(
                            "This is the response", completeResponse.aiMessage().text());
                    latch.countDown();
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking) {
                    assertEquals("I'm thinking", partialThinking.text());
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
                assertEquals(
                        com.ibm.watsonx.ai.chat.model.UserMessage.text("Hello"),
                        chatRequestCaptor.getValue().messages().get(0));
                assertNotNull(chatRequestCaptor.getValue().thinking());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testDoChatWithRefusal() {

        var messages = List.<ChatMessage>of(com.ibm.watsonx.ai.chat.model.UserMessage.text("Hello"));
        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);

                    for (String response : List.of("Hello", "World")) handler.onPartialResponse(response, null);

                    var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello World", null, "refusal", null);
                    var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
                    chatResponse.choices(List.of(resultChoice));
                    handler.onCompleteResponse(chatResponse.build());

                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withChatServiceMock(() -> {
            var streamingChatModel = WatsonxStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("projectId")
                    .spaceId("spaceId")
                    .apiKey("api-key")
                    .build();

            var chatRequest =
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build();

            var receivedResponses = new ArrayList<>();
            var latch = new CountDownLatch(2);

            var streamingHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    receivedResponses.add(partialResponse);
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    assertEquals("Hello World", completeResponse.aiMessage().text());
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    assertInstanceOf(ContentFilteredException.class, error);
                    assertEquals(error.getMessage(), "refusal");
                    latch.countDown();
                }
            };

            streamingChatModel.chat(chatRequest, streamingHandler);
            assertEquals(messages, chatRequestCaptor.getValue().messages());
            var parameters = chatRequestCaptor.getValue().parameters();

            try {
                boolean completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
                assertEquals(List.of("Hello", "World"), receivedResponses);
                assertNull(parameters.frequencyPenalty());
                assertNull(parameters.jsonSchema());
                assertNull(parameters.logitBias());
                assertNull(parameters.logprobs());
                assertNull(parameters.maxCompletionTokens());
                assertEquals("modelId", parameters.modelId());
                assertNull(parameters.n());
                assertNull(parameters.presencePenalty());
                assertEquals("projectId", parameters.projectId());
                assertNull(parameters.responseFormat());
                assertNull(parameters.seed());
                assertEquals("spaceId", parameters.spaceId());
                assertEquals(List.of(), parameters.stop());
                assertNull(parameters.temperature());
                assertNull(parameters.timeLimit());
                assertNull(parameters.toolChoice());
                assertNull(parameters.toolChoiceOption());
                assertNull(parameters.topLogprobs());
                assertNull(parameters.topP());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    public void testDoChatWithTool() throws Exception {

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
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withChatServiceMock(() -> {
            StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("projectId")
                    .spaceId("spaceId")
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
                    assertEquals("modelId", completeResponse.modelName());
                    assertEquals("modelVersion", metadata.getModelVersion());
                    assertEquals(1L, metadata.getCreated());
                    assertEquals(FinishReason.TOOL_EXECUTION, completeResponse.finishReason());
                    assertEquals(10, completeResponse.tokenUsage().inputTokenCount());
                    assertEquals(10, completeResponse.tokenUsage().outputTokenCount());
                    assertEquals(20, completeResponse.tokenUsage().totalTokenCount());
                    assertTrue(completeResponse.aiMessage().hasToolExecutionRequests());
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
                    assertEquals(
                            "id",
                            completeResponse
                                    .aiMessage()
                                    .toolExecutionRequests()
                                    .get(0)
                                    .id());
                    assertEquals(
                            "{}",
                            completeResponse
                                    .aiMessage()
                                    .toolExecutionRequests()
                                    .get(0)
                                    .arguments());
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
                public void onPartialThinking(PartialThinking partialThinking) {
                    assertEquals("test", partialThinking.text());
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
    void testJsonSchema() throws Exception {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onCompleteResponse(chatResponse.build());
                    handler.onError(new Exception("test"));
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        withChatServiceMock(() -> {
            var streamingChatModel = WatsonxStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("modelId")
                    .projectId("projectId")
                    .spaceId("spaceId")
                    .apiKey("api-key")
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
                    assertEquals("test", error.getMessage());
                }
            };

            streamingChatModel.chat("Hello", streamingHandler);
            var parameters = chatRequestCaptor.getValue().parameters();

            try {
                var completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
                assertEquals("json_schema", parameters.responseFormat());
                assertNotNull(parameters.jsonSchema());
                assertEquals("required", parameters.toolChoiceOption());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testChatRequestWithTopK() {

        var streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .modelName("modelId")
                .projectId("project-id")
                .spaceId("space-id")
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
                            public void onPartialResponse(String partialResponse) {
                                throw new UnsupportedOperationException("Unimplemented method 'onPartialResponse'");
                            }

                            @Override
                            public void onCompleteResponse(
                                    dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                                throw new UnsupportedOperationException("Unimplemented method 'onCompleteResponse'");
                            }

                            @Override
                            public void onError(Throwable error) {
                                throw new UnsupportedOperationException("Unimplemented method 'onError'");
                            }
                        }));

        assertThrows(UnsupportedFeatureException.class, () -> WatsonxStreamingChatModel.builder()
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

        var chatModel = WatsonxStreamingChatModel.builder()
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
