package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import com.ibm.watsonx.ai.rerank.RerankParameters;
import com.ibm.watsonx.ai.rerank.RerankPayload;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankInputResult;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankResult;
import com.ibm.watsonx.ai.rerank.RerankService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.listener.ScoringModelErrorContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import dev.langchain4j.model.watsonx.utils.HttpUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.ArrayList;
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
        when(mockRerankServiceBuilder.baseUrl(any(URI.class))).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.projectId(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.spaceId(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.timeout(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.version(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.logRequests(nullable(Boolean.class))).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.logResponses(nullable(Boolean.class))).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.authenticator(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.apiKey(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.httpClient(any())).thenReturn(mockRerankServiceBuilder);
        when(mockRerankServiceBuilder.verifySsl(anyBoolean())).thenReturn(mockRerankServiceBuilder);
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
        var mockAuthenticatorProvider = mock(IBMCloudAuthenticator.class);
        var mockHttpRequest = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.token()).thenReturn("my-token");
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(Json.toJson(rerankResponse));

        when(mockHttpClient.send(mockHttpRequest.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(() -> HttpClientProvider.httpClient(true)).thenReturn(mockHttpClient);

            var scoringModel = WatsonxScoringModel.builder()
                    .baseUrl(CloudRegion.FRANKFURT)
                    .modelName("model-name")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .spaceId("space-id")
                    .version("my-version")
                    .logRequests(true)
                    .logResponses(true)
                    .authenticator(mockAuthenticatorProvider)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            scoringModel.scoreAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query");

            var rerankRequest = Json.fromJson(HttpUtils.bodyPublisherToString(mockHttpRequest), RerankPayload.class);
            assertEquals("model-name", rerankRequest.modelId());
            assertEquals("project-id", rerankRequest.projectId());
            assertEquals("space-id", rerankRequest.spaceId());

            assertDoesNotThrow(() -> WatsonxScoringModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("model-name")
                    .apiKey("api-key")
                    .projectId("project-id")
                    .spaceId("space-id")
                    .build());
        }
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
                    .baseUrl("https://test.com")
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
                .returnQuery(true)
                .returnInputs(true)
                .build();

        withRerankServiceMock(() -> {
            WatsonxScoringModel scoringModel = WatsonxScoringModel.builder()
                    .baseUrl("https://test.com")
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

    @Test
    void should_notify_listeners_on_request_and_response() {

        List<RerankResult> rerankResults = List.of(
                new RerankResult(0, 0.0, new RerankInputResult("test1")),
                new RerankResult(1, 0.1, new RerankInputResult("test2")));
        RerankResponse rerankResponse =
                new RerankResponse("model-name-v2", rerankResults, "createdAt", 10, "modelVersion", "query");
        when(mockRerankService.rerank("query", List.of("test1", "test2"), null)).thenReturn(rerankResponse);
        List<Object> contexts = new ArrayList<>();

        withRerankServiceMock(() -> {
            ScoringModel scoringModel = modelWithListener(contexts);

            scoringModel.scoreAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query");

            assertEquals(2, contexts.size());
            var requestContext = (ScoringModelRequestContext) contexts.get(0);
            assertEquals(
                    List.of("test1", "test2"), requestContext.scoringRequest().documents());
            assertEquals("query", requestContext.scoringRequest().query());
            assertEquals("model-name", requestContext.scoringRequest().modelName());
            assertEquals(ModelProvider.WATSONX, requestContext.modelProvider());
            var responseContext = (ScoringModelResponseContext) contexts.get(1);
            assertEquals(List.of(0.0, 0.1), responseContext.scoringResponse().scores());
            assertEquals("model-name-v2", responseContext.scoringResponse().modelName());
            assertEquals(10, responseContext.scoringResponse().tokenUsage().inputTokenCount());
            assertSame(requestContext.attributes(), responseContext.attributes());
        });
    }

    @Test
    void should_notify_listeners_on_error() {

        when(mockRerankService.rerank("failing query", List.of("test1"), null)).thenThrow(new RuntimeException("boom"));
        List<Object> contexts = new ArrayList<>();

        withRerankServiceMock(() -> {
            ScoringModel scoringModel = modelWithListener(contexts);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> scoringModel.scoreAll(List.of(TextSegment.from("test1")), "failing query"));

            assertEquals(2, contexts.size());
            var requestContext = (ScoringModelRequestContext) contexts.get(0);
            var errorContext = (ScoringModelErrorContext) contexts.get(1);
            assertSame(thrown, errorContext.error());
            assertEquals("boom", errorContext.error().getMessage());
            assertEquals(ModelProvider.WATSONX, errorContext.modelProvider());
            assertSame(requestContext.attributes(), errorContext.attributes());
        });
    }

    @Test
    void score_should_send_the_per_call_model_name() {

        List<RerankResult> rerankResults = List.of(new RerankResult(0, 0.9, new RerankInputResult("test1")));
        RerankResponse rerankResponse =
                new RerankResponse("per-call-model", rerankResults, "createdAt", 1, "modelVersion", "query");
        ArgumentCaptor<RerankParameters> parameters = ArgumentCaptor.forClass(RerankParameters.class);
        when(mockRerankService.rerank(any(), any(), parameters.capture())).thenReturn(rerankResponse);
        List<Object> contexts = new ArrayList<>();

        withRerankServiceMock(() -> {
            ScoringModel scoringModel = modelWithListener(contexts);

            ScoringResponse response = scoringModel.score(ScoringRequest.builder()
                    .documents(List.of("test1"))
                    .query("query")
                    .parameters(ScoringRequestParameters.builder()
                            .modelName("per-call-model")
                            .build())
                    .build());

            assertEquals(List.of(0.9), response.scores());
            assertEquals("per-call-model", response.modelName());
            assertEquals("per-call-model", parameters.getValue().modelId());
            assertEquals(
                    "per-call-model",
                    ((ScoringModelRequestContext) contexts.get(0))
                            .scoringRequest()
                            .modelName());
        });
    }

    @Test
    void score_should_not_call_the_api_for_empty_documents_or_blank_query() {

        List<Object> contexts = new ArrayList<>();

        withRerankServiceMock(() -> {
            ScoringModel scoringModel = modelWithListener(contexts);

            ScoringResponse noDocuments = scoringModel.score(
                    ScoringRequest.builder().documents(List.of()).query("query").build());
            ScoringResponse blankQuery = scoringModel.score(ScoringRequest.builder()
                    .documents(List.of("test1"))
                    .query(" ")
                    .build());

            assertEquals(List.of(), noDocuments.scores());
            assertEquals(List.of(), blankQuery.scores());
            verifyNoInteractions(mockRerankService);
            assertEquals(4, contexts.size());
            assertEquals(
                    List.of(),
                    ((ScoringModelResponseContext) contexts.get(1))
                            .scoringResponse()
                            .scores());
        });
    }

    @Test
    void scoreAll_with_rerank_parameters_should_not_notify_listeners() {

        List<RerankResult> rerankResults = List.of(new RerankResult(0, 0.9, new RerankInputResult("test1")));
        RerankResponse rerankResponse =
                new RerankResponse("modelId", rerankResults, "createdAt", 1, "modelVersion", "query");
        RerankParameters parameters =
                RerankParameters.builder().modelId("modelId").build();
        when(mockRerankService.rerank("query", List.of("test1"), parameters)).thenReturn(rerankResponse);
        List<Object> contexts = new ArrayList<>();

        withRerankServiceMock(() -> {
            WatsonxScoringModel scoringModel = modelWithListener(contexts);

            var result = scoringModel.scoreAll(List.of(TextSegment.from("test1")), "query", parameters);

            assertEquals(List.of(0.9), result.content());
            assertEquals(0, contexts.size());
        });
    }

    private static WatsonxScoringModel modelWithListener(List<Object> contexts) {
        ScoringModelListener listener = new ScoringModelListener() {
            @Override
            public void onRequest(ScoringModelRequestContext requestContext) {
                contexts.add(requestContext);
            }

            @Override
            public void onResponse(ScoringModelResponseContext responseContext) {
                contexts.add(responseContext);
            }

            @Override
            public void onError(ScoringModelErrorContext errorContext) {
                contexts.add(errorContext);
            }
        };
        return WatsonxScoringModel.builder()
                .baseUrl("https://test.com")
                .modelName("model-name")
                .apiKey("api-key-test")
                .projectId("project-id")
                .listeners(listener)
                .build();
    }

    private void withRerankServiceMock(Runnable action) {
        try (MockedStatic<RerankService> mockedStatic = mockStatic(RerankService.class)) {
            mockedStatic.when(RerankService::builder).thenReturn(mockRerankServiceBuilder);
            action.run();
        }
    }
}
