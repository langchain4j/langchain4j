package dev.langchain4j.model.watsonx;

import static dev.langchain4j.model.ModelProvider.WATSONX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import dev.langchain4j.model.catalog.ModelType;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
public class WatsonxGatewayModelCatalogTest {

    @Test
    void should_list_models() throws Exception {

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticator = mock(IBMCloudAuthenticator.class);
        var mockHttpRequest = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticator.token()).thenReturn("my-super-token");
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn("""
                    {
                        "object": "list",
                        "data": [
                            {
                                "uuid": "123e4567-e89b-12d3-a456-426614174000",
                                "object": "model",
                                "created": 1677649963,
                                "owned_by": "openai",
                                "id": "gpt-4o",
                                "alias": "gpt-4o-alias",
                                "description": "A flagship model",
                                "metadata": {
                                    "cost": 0.02,
                                    "model_family": "gpt-4",
                                    "recommender_label": "gpt-4o",
                                    "region": "us-east-1",
                                    "batch": false,
                                    "context_window": 128000
                                }
                            },
                            {
                                "uuid": "223e4567-e89b-12d3-a456-426614174000",
                                "object": "model",
                                "id": "claude-sonnet-4-5",
                                "owned_by": "anthropic"
                            }
                        ]
                    }""");

        when(mockHttpClient.send(mockHttpRequest.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(() -> HttpClientProvider.httpClient(true)).thenReturn(mockHttpClient);

            var modelCatalog = WatsonxGatewayModelCatalog.builder()
                    .baseUrl(CloudRegion.FRANKFURT)
                    .authenticator(mockAuthenticator)
                    .build();

            var models = modelCatalog.listModels();

            assertTrue(mockHttpRequest.getValue().uri().getPath().endsWith("/ml/gateway/v1/models"));
            assertEquals(2, models.size());

            // The alias takes precedence over the provider model id.
            var model = models.get(0);
            assertEquals("gpt-4o-alias", model.name());
            assertEquals("gpt-4o-alias", model.displayName());
            assertEquals("A flagship model", model.description());
            assertEquals("openai", model.owner());
            assertEquals(WATSONX, model.provider());
            assertEquals(ModelType.CHAT, model.type());
            assertEquals("2023-03-01T05:52:43Z", model.createdAt().toString());
            assertEquals(128000, model.maxInputTokens());
            assertNull(model.maxOutputTokens());

            // No alias and only the mandatory fields are returned by the gateway.
            var minimalModel = models.get(1);
            assertEquals("claude-sonnet-4-5", minimalModel.name());
            assertEquals("claude-sonnet-4-5", minimalModel.displayName());
            assertEquals("anthropic", minimalModel.owner());
            assertEquals(ModelType.CHAT, minimalModel.type());
            assertNull(minimalModel.description());
            assertNull(minimalModel.createdAt());
            assertNull(minimalModel.maxInputTokens());
        }
    }

    @Test
    void should_throw_exception_when_no_credentials_are_provided() {
        assertThrows(
                NullPointerException.class,
                () -> WatsonxGatewayModelCatalog.builder()
                        .baseUrl(CloudRegion.FRANKFURT)
                        .build());
    }

    @Test
    void should_return_watsonx_provider() {

        var mockAuthenticator = mock(IBMCloudAuthenticator.class);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(() -> HttpClientProvider.httpClient(true)).thenReturn(mock(HttpClient.class));

            var modelCatalog = WatsonxGatewayModelCatalog.builder()
                    .baseUrl(CloudRegion.FRANKFURT)
                    .authenticator(mockAuthenticator)
                    .build();

            assertEquals(WATSONX, modelCatalog.provider());
        }
    }
}
