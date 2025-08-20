package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.FunctionCall;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WatsonxStreamingChatModelTest {

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
    public void testDoChat() throws Exception {

        var messages = List.<ChatMessage>of(com.ibm.watsonx.ai.chat.model.UserMessage.text("Hello"));
        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);

                    for (String response : List.of("Hello", "World")) handler.onPartialResponse(response, null);

                    var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello World", null, null);
                    var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
                    chatResponse.setChoices(List.of(resultChoice));
                    handler.onCompleteResponse(chatResponse);

                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        StreamingChatModel streamingChatModel =
                WatsonxStreamingChatModel.builder().service(mockChatService).build();

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hello")).build();

        List<String> receivedResponses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        StreamingChatResponseHandler streamingHandler = new StreamingChatResponseHandler() {
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
        assertEquals(messages, chatRequestCaptor.getValue().getMessages());
        var parameters = chatRequestCaptor.getValue().getParameters();

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Handler did not complete in time");
        assertEquals(List.of("Hello", "World"), receivedResponses);
        assertNull(parameters.getFrequencyPenalty());
        assertNull(parameters.getJsonSchema());
        assertNull(parameters.getLogitBias());
        assertNull(parameters.getLogprobs());
        assertNull(parameters.getMaxCompletionTokens());
        assertNull(parameters.getModelId());
        assertNull(parameters.getN());
        assertNull(parameters.getPresencePenalty());
        assertNull(parameters.getProjectId());
        assertNull(parameters.getResponseFormat());
        assertNull(parameters.getSeed());
        assertNull(parameters.getSpaceId());
        assertEquals(List.of(), parameters.getStop());
        assertNull(parameters.getTemperature());
        assertEquals(10000, parameters.getTimeLimit());
        assertNull(parameters.getToolChoice());
        assertNull(parameters.getToolChoiceOption());
        assertNull(parameters.getTopLogprobs());
        assertNull(parameters.getTopP());
    }

    @Test
    public void testDoChatWithTool() throws Exception {

        var toolCall = new ToolCall(0, "id", "function", new FunctionCall("name", "{}"));
        var resultMessage = new ResultMessage(AssistantMessage.ROLE, null, null, List.of(toolCall));
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "tool_calls");
        chatResponse.setChoices(List.of(resultChoice));

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onPartialThinking("test", null);
                    handler.onPartialToolCall(new com.ibm.watsonx.ai.chat.util.StreamingToolFetcher.PartialToolCall(
                            0, null, "name", "{"));
                    handler.onPartialToolCall(new com.ibm.watsonx.ai.chat.util.StreamingToolFetcher.PartialToolCall(
                            0, "id", "name", "}"));
                    handler.onCompleteToolCall(toolCall);
                    handler.onCompleteResponse(chatResponse);
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        StreamingChatModel streamingChatModel =
                WatsonxStreamingChatModel.builder().service(mockChatService).build();

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
                assertEquals("model", metadata.getModel());
                assertEquals("modelVersion", metadata.getModelVersion());
                assertEquals("object", metadata.getObject());
                assertEquals("createdAt", metadata.getCreatedAt());
                assertEquals(1L, metadata.getCreated());
                assertEquals(FinishReason.TOOL_EXECUTION, completeResponse.finishReason());
                assertEquals(10, completeResponse.tokenUsage().inputTokenCount());
                assertEquals(10, completeResponse.tokenUsage().outputTokenCount());
                assertEquals(20, completeResponse.tokenUsage().totalTokenCount());
                assertTrue(completeResponse.aiMessage().hasToolExecutionRequests());
                assertEquals(
                        1, completeResponse.aiMessage().toolExecutionRequests().size());
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
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Handler did not complete in time");
    }

    @Test
    void testJsonSchema() throws Exception {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null);
        var resultChoice = new ChatResponse.ResultChoice(0, resultMessage, "stop");
        chatResponse.setChoices(List.of(resultChoice));

        doAnswer(invocation -> {
                    ChatHandler handler = invocation.getArgument(1);
                    handler.onCompleteResponse(chatResponse);
                    handler.onError(new Exception("test"));
                    return CompletableFuture.completedFuture(null);
                })
                .when(mockChatService)
                .chatStreaming(chatRequestCaptor.capture(), any());

        StreamingChatModel streamingChatModel =
                WatsonxStreamingChatModel.builder().service(mockChatService).build();

        streamingChatModel = WatsonxStreamingChatModel.builder()
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

        CountDownLatch latch = new CountDownLatch(1);
        StreamingChatResponseHandler streamingHandler = new StreamingChatResponseHandler() {
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
        var parameters = chatRequestCaptor.getValue().getParameters();
        var completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Handler did not complete in time");
        assertEquals("json_schema", parameters.getResponseFormat());
        assertNotNull(parameters.getJsonSchema());
        assertEquals(1, streamingChatModel.supportedCapabilities().size());
        assertTrue(streamingChatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
        assertEquals("required", parameters.getToolChoiceOption());
    }
}
