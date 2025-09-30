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
import com.ibm.watsonx.ai.embedding.EmbeddingParameters;
import com.ibm.watsonx.ai.embedding.EmbeddingRequest;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
public class WatsonxEmbeddingModelTest {

    @Mock
    EmbeddingService mockEmbeddingService;

    @Mock
    EmbeddingService.Builder mockEmbeddingServiceBuilder;

    @BeforeEach
    void setUp() {
        when(mockEmbeddingServiceBuilder.modelId(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.url(any(URI.class))).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.projectId(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.spaceId(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.timeout(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.version(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.logRequests(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.logResponses(any())).thenReturn(mockEmbeddingServiceBuilder);
        when(mockEmbeddingServiceBuilder.build()).thenReturn(mockEmbeddingService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWatsonxEmbeddingModelBuilder() throws Exception {

        List<EmbeddingResponse.Result> results = List.of(new EmbeddingResponse.Result(List.of(0f, 1f), "test1"));

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticatorProvider = mock(IAMAuthenticator.class);
        var mockHttpRequest = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.token()).thenReturn("my-token");
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body())
                .thenReturn(Json.toJson(new EmbeddingResponse("modelId", "createdAt", results, 10)));
        when(mockHttpClient.send(mockHttpRequest.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(HttpClientProvider::httpClient).thenReturn(mockHttpClient);

            var embeddingModel = WatsonxEmbeddingModel.builder()
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

            embeddingModel.embed(TextSegment.from("test1"));

            var embeddingRequest =
                    Json.fromJson(HttpUtils.bodyPublisherToString(mockHttpRequest), EmbeddingRequest.class);
            assertEquals("model-name", embeddingRequest.modelId());
            assertEquals("project-id", embeddingRequest.projectId());
            assertEquals("space-id", embeddingRequest.spaceId());
            // 6. Test builder secondario
            assertDoesNotThrow(() -> WatsonxEmbeddingModel.builder()
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
    void testEmbeddingAll() {

        List<EmbeddingResponse.Result> results = List.of(
                new EmbeddingResponse.Result(List.of(0f, 1f), "test1"),
                new EmbeddingResponse.Result(List.of(0f, 1f), "test2"));

        when(mockEmbeddingService.embedding(List.of("test1", "test2"), null))
                .thenReturn(new EmbeddingResponse("modelId", "createdAt", results, 10));

        withEmbeddingServiceMock(() -> {
            EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
                    .url("https://test.com")
                    .projectId("projectId")
                    .modelName("modelName")
                    .apiKey("apiKey")
                    .build();

            var response = embeddingModel.embedAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")));
            assertEquals(2, response.content().size());
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(0));
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(1));
        });
    }

    @Test
    void testEmbeddingAllWithParameters() {

        List<EmbeddingResponse.Result> results = List.of(
                new EmbeddingResponse.Result(List.of(0f, 1f), "test1"),
                new EmbeddingResponse.Result(List.of(0f, 1f), "test2"));

        EmbeddingParameters parameters = EmbeddingParameters.builder()
                .modelId("modelId")
                .inputText(true)
                .projectId("projectId")
                .spaceId("spaceId")
                .truncateInputTokens(512)
                .build();

        withEmbeddingServiceMock(() -> {
            WatsonxEmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
                    .url("https://test.com")
                    .projectId("projectId")
                    .modelName("modelName")
                    .apiKey("apiKey")
                    .build();

            when(mockEmbeddingService.embedding(List.of("test1", "test2"), parameters))
                    .thenReturn(new EmbeddingResponse("modelId", "createdAt", results, 10));

            var response =
                    embeddingModel.embedAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), parameters);
            assertEquals(2, response.content().size());
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(0));
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(1));

            assertEquals(0, embeddingModel.embedAll(null).content().size());
            assertEquals(0, embeddingModel.embedAll(List.of()).content().size());
        });
    }

    private void withEmbeddingServiceMock(Runnable action) {
        try (MockedStatic<EmbeddingService> mockedStatic = mockStatic(EmbeddingService.class)) {
            mockedStatic.when(EmbeddingService::builder).thenReturn(mockEmbeddingServiceBuilder);
            action.run();
        }
    }
}
