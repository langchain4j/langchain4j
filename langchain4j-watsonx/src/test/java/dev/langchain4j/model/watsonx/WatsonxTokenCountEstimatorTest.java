package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationRequest;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse.Result;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.watsonx.utils.HttpUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WatsonxTokenCountEstimatorTest {

    @Mock
    TokenizationService mockTokenizationService;

    @Mock
    TokenizationService.Builder mockTokenizationServiceBuilder;

    @BeforeEach
    void setUp() {
        when(mockTokenizationServiceBuilder.modelId(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.url(any(URI.class))).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.projectId(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.spaceId(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.timeout(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.version(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.logRequests(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.logResponses(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.build()).thenReturn(mockTokenizationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWatsonxTokenCountEstimatorBuilder() throws Exception {

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticatorProvider = mock(IAMAuthenticator.class);
        var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.token()).thenReturn("my-token");

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body())
                .thenReturn(Json.toJson(new TokenizationResponse("my-model", new Result(10, List.of()))));

        when(mockHttpClient.send(requestCaptor.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(HttpClientProvider::httpClient).thenReturn(mockHttpClient);
            var tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                    .url(CloudRegion.FRANKFURT)
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .spaceId("space-id")
                    .version("my-version")
                    .logRequests(true)
                    .logResponses(true)
                    .authenticationProvider(mockAuthenticatorProvider)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            tokenCountEstimator.estimateTokenCountInText("tokenize");

            var tokenizationRequest =
                    Json.fromJson(HttpUtils.bodyPublisherToString(requestCaptor), TokenizationRequest.class);
            assertEquals("model-name", tokenizationRequest.modelId());
            assertEquals("project-id", tokenizationRequest.projectId());
            assertEquals("space-id", tokenizationRequest.spaceId());

            assertDoesNotThrow(() -> WatsonxScoringModel.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .authenticationProvider(
                            IAMAuthenticator.builder().apiKey("api-key").build())
                    .projectId("project-id")
                    .spaceId("space-id")
                    .build());
        }
    }

    @Test
    void testTokenize() {

        when(mockTokenizationService.tokenize("tokenize", null))
                .thenReturn(new TokenizationResponse("my-model", new Result(10, List.of())));

        withTokenizationServiceMock(() -> {
            TokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            var response = tokenCountEstimator.estimateTokenCountInText("tokenize");
            assertEquals(10, response);
        });
    }

    @Test
    void testTokenizeWithParameters() {

        var parameters = TokenizationParameters.builder()
                .projectId("project-id")
                .modelId("model-id")
                .returnTokens(true)
                .spaceId("space-id")
                .build();

        when(mockTokenizationService.tokenize("tokenize", parameters))
                .thenReturn(new TokenizationResponse("model-id", new Result(10, List.of())));

        withTokenizationServiceMock(() -> {
            WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            var response = tokenCountEstimator.estimateTokenCountInText("tokenize", parameters);
            assertEquals(10, response);
        });
    }

    @Test
    void testEstimateTokenCountInMessage() {

        withTokenizationServiceMock(() -> {
            WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> tokenCountEstimator.estimateTokenCountInMessage(UserMessage.builder()
                            .contents(List.of(ImageContent.from("test", DetailLevel.HIGH)))
                            .build()));

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> tokenCountEstimator.estimateTokenCountInMessage(CustomMessage.from(Map.of("test", "test"))));
        });
    }

    @Test
    void testEstimateTokenCountInMessages() {

        when(mockTokenizationService.tokenize(any(), isNull()))
                .thenReturn(new TokenizationResponse("model-id", new Result(1, null)));

        when(mockTokenizationService.asyncTokenize(any()))
                .thenReturn(
                        CompletableFuture.completedFuture(new TokenizationResponse("model-id", new Result(1, null))));

        withTokenizationServiceMock(() -> {
            WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            Iterable<ChatMessage> messages = List.of(
                    SystemMessage.from("test"),
                    UserMessage.builder()
                            .name("test")
                            .contents(List.of(TextContent.from("test")))
                            .build(),
                    AiMessage.builder()
                            .thinking("test")
                            .text("test")
                            .toolExecutionRequests(List.of(
                                    ToolExecutionRequest.builder()
                                            .id("test")
                                            .name("test")
                                            .arguments("test")
                                            .build(),
                                    ToolExecutionRequest.builder()
                                            .id("test")
                                            .name("test")
                                            .build()))
                            .build(),
                    ToolExecutionResultMessage.from("test", "test", "test"));

            assertEquals(11, tokenCountEstimator.estimateTokenCountInMessages(messages));
        });
    }

    private void withTokenizationServiceMock(Runnable action) {
        try (MockedStatic<TokenizationService> mockedStatic = mockStatic(TokenizationService.class)) {
            mockedStatic.when(TokenizationService::builder).thenReturn(mockTokenizationServiceBuilder);
            action.run();
        }
    }
}
