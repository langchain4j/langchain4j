package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Think;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.deployment.DeploymentChatRequest;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.net.URI;
import java.util.List;
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
public class WatsonxDeploymentChatModelTest {

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

        var chatModel = assertDoesNotThrow(() -> WatsonxDeploymentChatModel.builder()
                .baseUrl(CloudRegion.FRANKFURT)
                .apiKey("api-key-test")
                .version("my-version")
                .logRequests(true)
                .logResponses(true)
                .deploymentId("deployment-id")
                .build());

        var defaultRequestParameters =
                assertInstanceOf(WatsonxChatRequestParameters.class, chatModel.defaultRequestParameters());

        var deploymentServiceField =
                assertDoesNotThrow(() -> chatModel.getClass().getSuperclass().getDeclaredField("deploymentService"));
        var deploymentService = assertDoesNotThrow(() -> deploymentServiceField.get(chatModel));

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

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockDeploymentService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withDeploymentServiceMock(() -> {
            var chatModel = WatsonxDeploymentChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
                    .apiKey("api-key")
                    .build();

            assertEquals("Hello", chatModel.chat("hello"));
            assertEquals(
                    List.of(UserMessage.text("hello")),
                    chatRequestCaptor.getValue().messages());
            // deploymentId is a connection-level selector fixed at build time, carried on every request
            assertEquals("deployment-id", chatRequestCaptor.getValue().deploymentId());
        });
    }

    @Test
    void should_do_chat_with_refusal() {

        var resultMessage = new ResultMessage(AssistantMessage.ROLE, "Hello", null, "refusal", null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockDeploymentService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withDeploymentServiceMock(() -> {
            var chatModel = WatsonxDeploymentChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
                    .apiKey("api-key")
                    .build();

            assertThrows(ContentFilteredException.class, () -> chatModel.chat("hello"), "refusal");
        });
    }

    @Test
    void should_not_expose_foundation_model_selectors() throws Exception {

        // deploymentId already identifies both the model and its project or space
        var builderClass = WatsonxDeploymentChatModel.Builder.class;
        assertThrows(NoSuchMethodException.class, () -> builderClass.getMethod("modelName", String.class));
        assertThrows(NoSuchMethodException.class, () -> builderClass.getMethod("projectId", String.class));
        assertThrows(NoSuchMethodException.class, () -> builderClass.getMethod("spaceId", String.class));
        assertNotNull(builderClass.getMethod("deploymentId", String.class));
    }

    @Test
    void should_throw_exception_for_chat_request_with_top_k() {

        var chatModel = WatsonxDeploymentChatModel.builder()
                .baseUrl("https://test.com")
                .deploymentId("deployment-id")
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
                () -> WatsonxDeploymentChatModel.builder()
                        .baseUrl("https://test.com")
                        .deploymentId("deployment-id")
                        .apiKey("api-key")
                        .defaultRequestParameters(
                                ChatRequestParameters.builder().topK(10).build())
                        .build());
    }

    @Test
    void should_throw_exception_for_chat_request_with_model_name() {

        var chatModel = WatsonxDeploymentChatModel.builder()
                .baseUrl("https://test.com")
                .deploymentId("deployment-id")
                .apiKey("api-key")
                .build();

        assertThrows(
                UnsupportedFeatureException.class,
                () -> chatModel.chat(ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                        .modelName("my-model")
                        .build()));

        assertThrows(
                UnsupportedFeatureException.class,
                () -> WatsonxDeploymentChatModel.builder()
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

        var chatModel = WatsonxDeploymentChatModel.builder()
                .baseUrl("https://test.com")
                .deploymentId("deployment-id")
                .apiKey("api-key")
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertEquals(1, chatModel.supportedCapabilities().size());
        assertTrue(chatModel.supportedCapabilities().contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA));
    }

    @Test
    void should_authenticate_with_an_authenticator_instead_of_an_api_key() {

        var authenticator = mock(IBMCloudAuthenticator.class);

        withDeploymentServiceMock(() -> {
            assertDoesNotThrow(() -> WatsonxDeploymentChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
                    .authenticator(authenticator)
                    .build());

            verify(mockDeploymentServiceBuilder).authenticator(authenticator);
            verify(mockDeploymentServiceBuilder, never()).apiKey(any());
        });
    }

    @Test
    void should_send_thinking_on_the_deployment_request() {

        var resultMessage = new ResultMessage(
                AssistantMessage.ROLE, "<think>I'm thinking</think><response>Hello</response>", null, null, null);
        var resultChoice = new ResultChoice(0, resultMessage, "stop");
        chatResponse.choices(List.of(resultChoice));

        when(mockDeploymentService.chat(chatRequestCaptor.capture())).thenReturn(chatResponse.build());

        withDeploymentServiceMock(() -> {
            var chatModel = WatsonxDeploymentChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
                    .apiKey("api-key")
                    .thinking(ExtractionTags.of(new Think("<think>", "</think>")))
                    .build();

            chatModel.chat("hello");
            assertNotNull(chatRequestCaptor.getValue().thinking());
        });

        withDeploymentServiceMock(() -> {
            var chatModel = WatsonxDeploymentChatModel.builder()
                    .baseUrl("https://test.com")
                    .deploymentId("deployment-id")
                    .apiKey("api-key")
                    .build();

            chatModel.chat("hello");
            assertNull(chatRequestCaptor.getValue().thinking());
        });
    }

    private void withDeploymentServiceMock(Runnable action) {
        try (MockedStatic<DeploymentService> mockedStatic = mockStatic(DeploymentService.class)) {
            mockedStatic.when(DeploymentService::builder).thenReturn(mockDeploymentServiceBuilder);
            action.run();
        }
    }
}
