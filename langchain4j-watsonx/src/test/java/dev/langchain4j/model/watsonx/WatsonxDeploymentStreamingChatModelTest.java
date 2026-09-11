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
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.deployment.DeploymentChatRequest;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
public class WatsonxDeploymentStreamingChatModelTest {

    @Mock
    DeploymentService mockDeploymentService;

    @Mock
    DeploymentService.Builder mockDeploymentServiceBuilder;

    @Captor
    ArgumentCaptor<DeploymentChatRequest> chatRequestCaptor;

    static TextChatResponse.Builder<?> chatResponse;

    @BeforeEach
    void setUp() {

        when(mockDeploymentServiceBuilder.baseUrl(any(URI.class))).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.timeout(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.version(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.logRequests(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.logResponses(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.authenticator(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.apiKey(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.httpClient(any())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.verifySsl(anyBoolean())).thenReturn(mockDeploymentServiceBuilder);
        when(mockDeploymentServiceBuilder.build()).thenReturn(mockDeploymentService);

        var chatUsage = new ChatUsage(10, 10, 20);
        chatResponse = TextChatResponse.builder()
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
    void should_create_a_watsonx_chat_model_from_a_deployment_service() {

        var streamingChatModel = assertDoesNotThrow(() -> WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl(CloudRegion.FRANKFURT)
                .apiKey("api-key-test")
                .version("my-version")
                .logRequests(true)
                .logResponses(true)
                .deploymentId("deployment-id")
                .build());

        var defaultRequestParameters =
                assertInstanceOf(WatsonxChatRequestParameters.class, streamingChatModel.defaultRequestParameters());

        var deploymentServiceField = assertDoesNotThrow(
                () -> streamingChatModel.getClass().getSuperclass().getDeclaredField("deploymentService"));
        var deploymentService = assertDoesNotThrow(() -> deploymentServiceField.get(streamingChatModel));

        assertInstanceOf(DeploymentService.class, deploymentService);
        assertNull(defaultRequestParameters.frequencyPenalty());
        assertNull(defaultRequestParameters.logitBias());
        assertNull(defaultRequestParameters.logprobs());
        assertNull(defaultRequestParameters.maxOutputTokens());
        assertNull(defaultRequestParameters.modelName());
        assertNull(defaultRequestParameters.presencePenalty());
        assertNull(defaultRequestParameters.projectId());
        assertNull(defaultRequestParameters.responseFormat());
        assertNull(defaultRequestParameters.seed());
        assertNull(defaultRequestParameters.spaceId());
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
    }

    @Test
    void should_do_chat() {

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
                .when(mockDeploymentService)
                .chatStreaming(chatRequestCaptor.capture(), any(ChatHandler.class));

        withDeploymentServiceMock(() -> {
            var streamingChatModel = WatsonxDeploymentStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
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
            // deploymentId is a connection-level selector fixed at build time, carried on every request
            assertEquals("deployment-id", chatRequestCaptor.getValue().deploymentId());

            try {
                boolean completed = latch.await(2, TimeUnit.SECONDS);
                assertTrue(completed, "Handler did not complete in time");
                assertEquals(List.of("Hello", "World"), receivedResponses);
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
                .when(mockDeploymentService)
                .chatStreaming(chatRequestCaptor.capture(), any(ChatHandler.class));

        withDeploymentServiceMock(() -> {
            var streamingChatModel = WatsonxDeploymentStreamingChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
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
                assertTrue(latch.await(2, TimeUnit.SECONDS), "Handler did not complete in time");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_not_expose_foundation_model_selectors() throws Exception {

        // deploymentId already identifies both the model and its project or space
        var builderClass = WatsonxDeploymentStreamingChatModel.Builder.class;
        assertThrows(NoSuchMethodException.class, () -> builderClass.getMethod("modelName", String.class));
        assertThrows(NoSuchMethodException.class, () -> builderClass.getMethod("projectId", String.class));
        assertThrows(NoSuchMethodException.class, () -> builderClass.getMethod("spaceId", String.class));
        assertNotNull(builderClass.getMethod("deploymentId", String.class));
    }

    @Test
    void should_throw_exception_for_chat_request_with_top_k() {

        var streamingChatModel = WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .deploymentId("deployment-id")
                .apiKey("api-key")
                .build();

        assertThrows(
                UnsupportedFeatureException.class,
                () -> streamingChatModel.chat(
                        ChatRequest.builder()
                                .messages(UserMessage.from("Hello"))
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

        assertThrows(
                UnsupportedFeatureException.class,
                () -> WatsonxDeploymentStreamingChatModel.builder()
                        .baseUrl("https://test.com")
                        .deploymentId("deployment-id")
                        .apiKey("api-key")
                        .defaultRequestParameters(
                                ChatRequestParameters.builder().topK(10).build())
                        .build());
    }

    @Test
    void should_throw_exception_for_chat_request_with_model_name() {

        var streamingChatModel = WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .deploymentId("deployment-id")
                .apiKey("api-key")
                .build();

        assertThrows(
                UnsupportedFeatureException.class,
                () -> streamingChatModel.chat(
                        ChatRequest.builder()
                                .messages(UserMessage.from("Hello"))
                                .modelName("my-model")
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

        assertThrows(
                UnsupportedFeatureException.class,
                () -> WatsonxDeploymentStreamingChatModel.builder()
                        .baseUrl("https://test.com")
                        .deploymentId("deployment-id")
                        .apiKey("api-key")
                        .defaultRequestParameters(ChatRequestParameters.builder()
                                .modelName("my-model")
                                .build())
                        .build());
    }

    @Test
    void should_support_capabilities() {

        var streamingChatModel = WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl("https://test.com")
                .deploymentId("deployment-id")
                .apiKey("api-key")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertEquals(1, streamingChatModel.supportedCapabilities().size());
        assertTrue(streamingChatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
    }

    private void withDeploymentServiceMock(Runnable action) {
        try (MockedStatic<DeploymentService> mockedStatic = mockStatic(DeploymentService.class)) {
            mockedStatic.when(DeploymentService::builder).thenReturn(mockDeploymentServiceBuilder);
            action.run();
        }
    }
}
