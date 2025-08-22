package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationRequest;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse.Result;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
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
        when(mockTokenizationServiceBuilder.httpClient(any())).thenReturn(mockTokenizationServiceBuilder);
        when(mockTokenizationServiceBuilder.build()).thenReturn(mockTokenizationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWatsonxTokenCountEstimatorBuilder() throws Exception {

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticatorProvider = mock(IAMAuthenticator.class);
        var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.getToken()).thenReturn("my-token");

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body())
                .thenReturn(Json.toJson(new TokenizationResponse("my-model", new Result(10, List.of()))));

        when(mockHttpClient.send(requestCaptor.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

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
                .httpClient(mockHttpClient)
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
                    () -> tokenCountEstimator.estimateTokenCountInMessage(UserMessage.from("test")));
        });
    }

    @Test
    void testEstimateTokenCountInMessages() {

        withTokenizationServiceMock(() -> {
            WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> tokenCountEstimator.estimateTokenCountInMessages(List.of(UserMessage.from("test"))));
        });
    }

    private void withTokenizationServiceMock(Runnable action) {
        try (MockedStatic<TokenizationService> mockedStatic = mockStatic(TokenizationService.class)) {
            mockedStatic.when(TokenizationService::builder).thenReturn(mockTokenizationServiceBuilder);
            action.run();
        }
    }
}
