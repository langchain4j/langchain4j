package dev.langchain4j.model.watsonx;

import static dev.langchain4j.model.ModelProvider.WATSONX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingParameters;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingParameters.EncodingFormat;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingPayload;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingResponse;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingResponse.Usage;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.watsonx.utils.HttpUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
public class WatsonxGatewayEmbeddingModelTest {

    @Mock
    ModelGatewayEmbeddingService mockModelGatewayEmbeddingService;

    @Mock
    ModelGatewayEmbeddingService.Builder mockModelGatewayEmbeddingServiceBuilder;

    @Captor
    ArgumentCaptor<ModelGatewayEmbeddingParameters> parametersCaptor;

    @BeforeEach
    void setUp() {
        when(mockModelGatewayEmbeddingServiceBuilder.modelId(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.baseUrl(any(URI.class)))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.timeout(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.version(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.logRequests(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.logResponses(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.authenticator(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.apiKey(any())).thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.httpClient(any()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.verifySsl(anyBoolean()))
                .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
        when(mockModelGatewayEmbeddingServiceBuilder.build()).thenReturn(mockModelGatewayEmbeddingService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_create_a_watsonx_gateway_embedding_model() throws Exception {

        var data = List.of(new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticatorProvider = mock(IBMCloudAuthenticator.class);
        var mockHttpRequest = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.token()).thenReturn("my-token");
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body())
                .thenReturn(Json.toJson(
                        new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, new Usage(10, 10))));
        when(mockHttpClient.send(mockHttpRequest.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(() -> HttpClientProvider.httpClient(true)).thenReturn(mockHttpClient);

            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl(CloudRegion.FRANKFURT)
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key-test")
                    .version("my-version")
                    .dimensions(512)
                    .encodingFormat(EncodingFormat.BASE64)
                    .user("my-user")
                    .logRequests(true)
                    .logResponses(true)
                    .authenticator(mockAuthenticatorProvider)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            var response = embeddingModel.embed(TextSegment.from("test1"));

            var payload =
                    Json.fromJson(HttpUtils.bodyPublisherToString(mockHttpRequest), ModelGatewayEmbeddingPayload.class);

            assertEquals("text-embedding-3-small", payload.model());
            assertEquals(List.of("test1"), payload.input());
            assertEquals(512, payload.dimensions());
            assertEquals("base64", payload.encodingFormat());
            assertEquals("my-user", payload.user());

            var serviceField = assertDoesNotThrow(
                    () -> embeddingModel.getClass().getDeclaredField("modelGatewayEmbeddingService"));
            serviceField.setAccessible(true);
            var service = assertDoesNotThrow(() -> serviceField.get(embeddingModel));

            assertInstanceOf(ModelGatewayEmbeddingService.class, service);
            assertEquals("text-embedding-3-small", embeddingModel.modelName());
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content());
            assertEquals(10, response.tokenUsage().inputTokenCount());

            assertDoesNotThrow(() -> WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .build());
        }
    }

    @Test
    void should_embed_all() {

        var data = List.of(
                new ModelGatewayEmbeddingResponse.Embedding("embedding", 1, List.of(2f, 3f), null),
                new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));

        when(mockModelGatewayEmbeddingService.embed(
                        List.of("test1", "test2"),
                        ModelGatewayEmbeddingParameters.builder().build()))
                .thenReturn(
                        new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, new Usage(10, 10)));

        withModelGatewayEmbeddingServiceMock(() -> {
            EmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .build();

            var response = embeddingModel.embedAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")));

            assertEquals(2, response.content().size());
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(0));
            assertEquals(Embedding.from(List.of(2f, 3f)), response.content().get(1));
            assertEquals(10, response.tokenUsage().inputTokenCount());
        });
    }

    @Test
    void should_embed_all_with_the_builder_parameters() {

        var data = List.of(new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));

        when(mockModelGatewayEmbeddingService.embed(any(), parametersCaptor.capture()))
                .thenReturn(new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, null));

        withModelGatewayEmbeddingServiceMock(() -> {
            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .dimensions(512)
                    .encodingFormat("float")
                    .user("my-user")
                    .build();

            var response = embeddingModel.embedAll(List.of(TextSegment.from("test1")));
            var parameters = parametersCaptor.getValue();

            assertEquals(512, parameters.dimensions());
            assertEquals("float", parameters.encodingFormat());
            assertEquals("my-user", parameters.user());
            assertEquals(1, response.content().size());
            assertNull(response.tokenUsage());
        });
    }

    @Test
    void should_override_the_builder_parameters_with_the_given_parameters() {

        var data = List.of(new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));

        var parameters = ModelGatewayEmbeddingParameters.builder()
                .dimensions(256)
                .encodingFormat(EncodingFormat.FLOAT)
                .build();

        when(mockModelGatewayEmbeddingService.embed(List.of("test1"), parameters))
                .thenReturn(
                        new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, new Usage(10, 10)));

        withModelGatewayEmbeddingServiceMock(() -> {
            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .dimensions(512)
                    .user("my-user")
                    .build();

            var response = embeddingModel.embedAll(List.of(TextSegment.from("test1")), parameters);

            assertEquals(1, response.content().size());
            assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(0));
            assertEquals(10, response.tokenUsage().inputTokenCount());
        });
    }

    @Test
    void should_return_no_embeddings_when_there_is_no_input() {

        withModelGatewayEmbeddingServiceMock(() -> {
            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .build();

            assertEquals(0, embeddingModel.embedAll(null).content().size());
            assertEquals(0, embeddingModel.embedAll(List.of()).content().size());
            assertEquals(
                    0,
                    embeddingModel
                            .embedAll(
                                    List.of(),
                                    ModelGatewayEmbeddingParameters.builder().build())
                            .content()
                            .size());
        });
    }

    @Test
    void should_override_the_builder_dimensions_with_the_dimensions_of_the_request() {

        var data = List.of(new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));

        when(mockModelGatewayEmbeddingService.embed(any(), parametersCaptor.capture()))
                .thenReturn(
                        new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, new Usage(10, 10)));

        withModelGatewayEmbeddingServiceMock(() -> {
            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .dimensions(512)
                    .user("my-user")
                    .build();

            assertEquals(Set.of(EmbeddingRequestParameters.DIMENSIONS), embeddingModel.supportedParameters());
            assertEquals(512, embeddingModel.defaultRequestParameters().dimensions());

            var response = embeddingModel.embed(
                    EmbeddingRequest.builder().input("test1").dimensions(256).build());

            assertEquals(256, parametersCaptor.getValue().dimensions());
            assertEquals("my-user", parametersCaptor.getValue().user());
            assertEquals("text-embedding-3-small", response.metadata().modelName());
            assertEquals(10, response.metadata().tokenUsage().inputTokenCount());
        });
    }

    @Test
    void should_reject_the_parameters_the_gateway_does_not_support() {

        withModelGatewayEmbeddingServiceMock(() -> {
            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .build();

            assertThrows(
                    UnsupportedFeatureException.class,
                    () -> embeddingModel.embed(EmbeddingRequest.builder()
                            .input("test1")
                            .inputType(EmbeddingInputType.QUERY)
                            .build()));
        });
    }

    @Test
    void should_notify_the_listeners_when_embedding_with_the_gateway_parameters() {

        var data = List.of(new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));
        var parameters = ModelGatewayEmbeddingParameters.builder().dimensions(256).build();

        when(mockModelGatewayEmbeddingService.embed(any(), parametersCaptor.capture()))
                .thenReturn(
                        new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, new Usage(10, 10)));

        var requestContext = new AtomicReference<EmbeddingModelRequestContext>();
        var responseContext = new AtomicReference<EmbeddingModelResponseContext>();

        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext context) {
                requestContext.set(context);
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext context) {
                responseContext.set(context);
            }
        };

        withModelGatewayEmbeddingServiceMock(() -> {
            var embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .dimensions(512)
                    .listeners(List.of(listener))
                    .build();

            var response = embeddingModel.embedAll(List.of(TextSegment.from("test1")), parameters);

            assertEquals(256, parametersCaptor.getValue().dimensions());
            assertEquals(1, response.content().size());

            assertNotNull(requestContext.get());
            assertEquals(WATSONX, requestContext.get().modelProvider());
            assertEquals(
                    List.of("test1"),
                    requestContext.get().textSegments().stream()
                            .map(TextSegment::text)
                            .toList());

            assertNotNull(responseContext.get());
            assertEquals(
                    1, responseContext.get().embeddingResponse().embeddings().size());
            assertEquals(10, responseContext.get().response().tokenUsage().inputTokenCount());
        });
    }

    @Test
    void should_notify_the_listeners() {

        var data = List.of(new ModelGatewayEmbeddingResponse.Embedding("embedding", 0, List.of(0f, 1f), null));

        when(mockModelGatewayEmbeddingService.embed(any(), any()))
                .thenReturn(
                        new ModelGatewayEmbeddingResponse("list", "text-embedding-3-small", data, new Usage(10, 10)));

        var requestContext = new AtomicReference<EmbeddingModelRequestContext>();
        var responseContext = new AtomicReference<EmbeddingModelResponseContext>();

        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext context) {
                requestContext.set(context);
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext context) {
                responseContext.set(context);
            }
        };

        withModelGatewayEmbeddingServiceMock(() -> {
            EmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .apiKey("api-key")
                    .listeners(List.of(listener))
                    .build();

            embeddingModel.embed("test1");

            assertNotNull(requestContext.get());
            assertEquals(WATSONX, requestContext.get().modelProvider());
            assertNotNull(responseContext.get());
            assertEquals(
                    1, responseContext.get().embeddingResponse().embeddings().size());
        });
    }

    @Test
    void should_authenticate_with_an_authenticator_instead_of_an_api_key() {

        var authenticator = mock(IBMCloudAuthenticator.class);

        withModelGatewayEmbeddingServiceMock(() -> {
            assertDoesNotThrow(() -> WatsonxGatewayEmbeddingModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("text-embedding-3-small")
                    .authenticator(authenticator)
                    .build());

            verify(mockModelGatewayEmbeddingServiceBuilder).authenticator(authenticator);
            verify(mockModelGatewayEmbeddingServiceBuilder, never()).apiKey(any());
        });
    }

    private void withModelGatewayEmbeddingServiceMock(Runnable action) {
        try (MockedStatic<ModelGatewayEmbeddingService> mockedStatic = mockStatic(ModelGatewayEmbeddingService.class)) {
            mockedStatic
                    .when(ModelGatewayEmbeddingService::builder)
                    .thenReturn(mockModelGatewayEmbeddingServiceBuilder);
            action.run();
        }
    }
}
