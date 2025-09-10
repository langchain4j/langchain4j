package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import com.ibm.watsonx.ai.rerank.RerankParameters;
import com.ibm.watsonx.ai.rerank.RerankRequest;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankInputResult;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankResult;
import com.ibm.watsonx.ai.rerank.RerankService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
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
public class WatsonxScoringModelTest {

    @Mock
    RerankService mockRerankService;

    @Mock
    RerankService.Builder mockRerankServiceBuilder;

    @BeforeEach
    void setUp() {
        when(mockRerankServiceBuilder.modelId(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.url(any(URI.class))).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.projectId(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.spaceId(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.timeout(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.version(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.logRequests(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.logResponses(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.build()).thenReturn(mockRerankService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWatsonxRerankModelBuilder() throws Exception {

        List<RerankResult> rerankResults = List.of(
                new RerankResult(0, 0.0, new RerankInputResult("test1")),
                new RerankResult(1, 0.1, new RerankInputResult("test2")));
        RerankResponse rerankResponse =
                new RerankResponse("modelId", rerankResults, "createdAt", 10, "modelVersion", "query");

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticatorProvider = mock(IAMAuthenticator.class);
        var mockHttpRequest = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.token()).thenReturn("my-token");
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(Json.toJson(rerankResponse));

        when(mockHttpClient.send(mockHttpRequest.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(HttpClientProvider::httpClient).thenReturn(mockHttpClient);

            var scoringModel = WatsonxScoringModel.builder()
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

            scoringModel.scoreAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query");

            var rerankRequest = Json.fromJson(HttpUtils.bodyPublisherToString(mockHttpRequest), RerankRequest.class);
            assertEquals("model-name", rerankRequest.modelId());
            assertEquals("project-id", rerankRequest.projectId());
            assertEquals("space-id", rerankRequest.spaceId());

            assertDoesNotThrow(() -> WatsonxScoringModel.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .authenticationProvider(
                            IAMAuthenticator.builder().apiKey("api-key").build())
                    .projectId("project-id")
                    .spaceId("space-id")
                    .build());
        }
        ;
    }

    @Test
    void testScoreAll() {

        List<RerankResult> rerankResults = List.of(
                new RerankResult(0, 0.0, new RerankInputResult("test1")),
                new RerankResult(1, 0.1, new RerankInputResult("test2")));
        RerankResponse rerankResponse =
                new RerankResponse("modelId", rerankResults, "createdAt", 10, "modelVersion", "query");

        when(mockRerankService.rerank("query", List.of("test1", "test2"), null)).thenReturn(rerankResponse);

        withRerankServiceMock(() -> {
            ScoringModel scoringModel = WatsonxScoringModel.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            var result = scoringModel.scoreAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query");
            assertEquals(2, result.content().size());
            assertEquals(0.0, result.content().get(0));
            assertEquals(0.1, result.content().get(1));
        });
    }

    @Test
    void testScoreAllWithParameters() {

        List<RerankResult> rerankResults = List.of(
                new RerankResult(0, 0.0, new RerankInputResult("test1")),
                new RerankResult(1, 0.1, new RerankInputResult("test2")));
        RerankResponse rerankResponse =
                new RerankResponse("modelId", rerankResults, "createdAt", 10, "modelVersion", "query");
        RerankParameters parameters = RerankParameters.builder()
                .modelId("modelId")
                .projectId("projectId")
                .spaceId("spaceId")
                .truncateInputTokens(512)
                .query(true)
                .inputs(true)
                .build();

        withRerankServiceMock(() -> {
            WatsonxScoringModel scoringModel = WatsonxScoringModel.builder()
                    .url("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .build();

            when(mockRerankService.rerank("query", List.of("test1", "test2"), parameters))
                    .thenReturn(rerankResponse);

            var result = scoringModel.scoreAll(
                    List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query", parameters);
            assertEquals(2, result.content().size());
            assertEquals(0.0, result.content().get(0));
            assertEquals(0.1, result.content().get(1));

            assertEquals(0, scoringModel.scoreAll(null, "query").content().size());
            assertEquals(0, scoringModel.scoreAll(List.of(), "query").content().size());
            assertEquals(
                    0,
                    scoringModel
                            .scoreAll(List.of(TextSegment.from("test1")), null)
                            .content()
                            .size());
            assertEquals(
                    0,
                    scoringModel
                            .scoreAll(List.of(TextSegment.from("test1")), "")
                            .content()
                            .size());
        });
    }

    private void withRerankServiceMock(Runnable action) {
        try (MockedStatic<RerankService> mockedStatic = mockStatic(RerankService.class)) {
            mockedStatic.when(RerankService::builder).thenReturn(mockRerankServiceBuilder);
            action.run();
        }
    }
}
