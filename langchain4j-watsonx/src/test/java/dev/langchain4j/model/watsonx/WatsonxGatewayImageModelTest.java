package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageGenerationRequest;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Background;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Moderation;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.OutputFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Quality;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.ResponseFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Size;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Style;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse.ImageData;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse.InputTokensDetails;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse.Usage;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageService;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.image.ImageModel;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WatsonxGatewayImageModelTest {

    @Mock
    ModelGatewayImageService mockModelGatewayImageService;

    @Mock
    ModelGatewayImageService.Builder mockModelGatewayImageServiceBuilder;

    @Captor
    ArgumentCaptor<ModelGatewayImageParameters> parametersCaptor;

    @BeforeEach
    void setUp() {
        when(mockModelGatewayImageServiceBuilder.modelId(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.baseUrl(any(URI.class)))
                .thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.timeout(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.version(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.logRequests(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.logResponses(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.authenticator(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.apiKey(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.httpClient(any())).thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.verifySsl(anyBoolean()))
                .thenReturn(mockModelGatewayImageServiceBuilder);
        when(mockModelGatewayImageServiceBuilder.build()).thenReturn(mockModelGatewayImageService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_create_a_watsonx_gateway_image_model() throws Exception {

        var data = List.of(new ImageData(null, "aGVsbG8=", "A revised prompt"));
        var usage = new Usage(10, 20, 30, new InputTokensDetails(0, 10));

        var mockHttpClient = mock(HttpClient.class);
        var mockHttpResponse = mock(HttpResponse.class);
        var mockAuthenticatorProvider = mock(IBMCloudAuthenticator.class);
        var mockHttpRequest = ArgumentCaptor.forClass(HttpRequest.class);

        when(mockAuthenticatorProvider.token()).thenReturn("my-token");
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body())
                .thenReturn(Json.toJson(
                        new ModelGatewayImageResponse(0, data, "opaque", "png", "high", "1024x1024", usage)));
        when(mockHttpClient.send(mockHttpRequest.capture(), any(BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClientProvider> httpClientProvider = mockStatic(HttpClientProvider.class)) {
            httpClientProvider.when(() -> HttpClientProvider.httpClient(true)).thenReturn(mockHttpClient);

            var imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl(CloudRegion.FRANKFURT)
                    .modelName("gpt-image-1")
                    .apiKey("api-key-test")
                    .version("my-version")
                    .background(Background.OPAQUE)
                    .moderation(Moderation.LOW)
                    .outputCompression(50)
                    .outputFormat(OutputFormat.PNG)
                    .quality(Quality.HIGH)
                    .responseFormat(ResponseFormat.B64_JSON)
                    .size(Size.SIZE_1024X1024)
                    .style(Style.VIVID)
                    .user("my-user")
                    .logRequests(true)
                    .logResponses(true)
                    .authenticator(mockAuthenticatorProvider)
                    .timeout(Duration.ofSeconds(10))
                    .build();

            var response = imageModel.generate("A futuristic city at sunset");

            var payload = Json.fromJson(
                    HttpUtils.bodyPublisherToString(mockHttpRequest), ModelGatewayImageGenerationRequest.class);

            assertEquals("gpt-image-1", payload.model());
            assertEquals("A futuristic city at sunset", payload.prompt());
            assertEquals("opaque", payload.background());
            assertEquals("low", payload.moderation());
            assertNull(payload.n());
            assertEquals(50, payload.outputCompression());
            assertEquals("png", payload.outputFormat());
            assertEquals("high", payload.quality());
            assertEquals("b64_json", payload.responseFormat());
            assertEquals("1024x1024", payload.size());
            assertEquals("vivid", payload.style());
            assertEquals("my-user", payload.user());

            var serviceField =
                    assertDoesNotThrow(() -> imageModel.getClass().getDeclaredField("modelGatewayImageService"));
            serviceField.setAccessible(true);
            var service = assertDoesNotThrow(() -> serviceField.get(imageModel));

            assertInstanceOf(ModelGatewayImageService.class, service);
            assertEquals("gpt-image-1", imageModel.modelName());

            var image = response.content();
            assertNull(image.url());
            assertEquals("aGVsbG8=", image.base64Data());
            assertEquals("image/png", image.mimeType());
            assertEquals("A revised prompt", image.revisedPrompt());
            assertEquals(10, response.tokenUsage().inputTokenCount());
            assertEquals(20, response.tokenUsage().outputTokenCount());
            assertEquals(30, response.tokenUsage().totalTokenCount());

            assertDoesNotThrow(() -> WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .build());
        }
    }

    @Test
    void should_generate_more_than_one_image() {

        var data = List.of(new ImageData("https://test.com/1.png", null, null), new ImageData(null, "aGVsbG8=", null));

        when(mockModelGatewayImageService.generate(any(String.class), parametersCaptor.capture()))
                .thenReturn(new ModelGatewayImageResponse(0, data, null, null, null, null, null));

        withModelGatewayImageServiceMock(() -> {
            ImageModel imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .size(Size.SIZE_1024X1024)
                    .user("my-user")
                    .build();

            var response = imageModel.generate("A futuristic city at sunset", 2);
            var parameters = parametersCaptor.getValue();

            assertEquals(2, parameters.n());
            assertEquals("1024x1024", parameters.size());
            assertEquals("my-user", parameters.user());

            assertEquals(2, response.content().size());
            assertEquals(
                    URI.create("https://test.com/1.png"),
                    response.content().get(0).url());
            assertNull(response.content().get(0).base64Data());
            assertNull(response.content().get(0).mimeType());
            assertEquals("aGVsbG8=", response.content().get(1).base64Data());
            assertNull(response.tokenUsage());
        });
    }

    @Test
    void should_return_the_first_image_when_the_model_generates_more_than_one() {

        var data = List.of(new ImageData(null, "Zmlyc3Q=", null), new ImageData(null, "c2Vjb25k", null));

        when(mockModelGatewayImageService.generate(any(String.class), any()))
                .thenReturn(new ModelGatewayImageResponse(0, data, null, "jpeg", null, null, null));

        withModelGatewayImageServiceMock(() -> {
            ImageModel imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .build();

            var response = imageModel.generate("A futuristic city at sunset");

            assertEquals("Zmlyc3Q=", response.content().base64Data());
            assertEquals("image/jpeg", response.content().mimeType());
        });
    }

    @Test
    void should_override_the_builder_parameters_with_the_given_parameters() {

        var data = List.of(new ImageData(null, "aGVsbG8=", null));

        var parameters = ModelGatewayImageParameters.builder()
                .n(1)
                .size(Size.SIZE_512X512)
                .quality(Quality.LOW)
                .build();

        when(mockModelGatewayImageService.generate("A futuristic city at sunset", parameters))
                .thenReturn(new ModelGatewayImageResponse(0, data, null, "png", null, null, null));

        withModelGatewayImageServiceMock(() -> {
            var imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .size(Size.SIZE_1024X1024)
                    .quality(Quality.HIGH)
                    .user("my-user")
                    .build();

            var response = imageModel.generate("A futuristic city at sunset", parameters);

            assertEquals(1, response.content().size());
            assertEquals("aGVsbG8=", response.content().get(0).base64Data());
        });
    }

    @Test
    void should_use_the_builder_parameters_when_no_parameters_are_given() {

        var data = List.of(new ImageData(null, "aGVsbG8=", null));

        when(mockModelGatewayImageService.generate(any(String.class), parametersCaptor.capture()))
                .thenReturn(new ModelGatewayImageResponse(0, data, null, null, null, null, null));

        withModelGatewayImageServiceMock(() -> {
            var imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .background("opaque")
                    .moderation("low")
                    .outputFormat("webp")
                    .quality("medium")
                    .responseFormat("b64_json")
                    .size("1536x1024")
                    .style("natural")
                    .outputCompression(80)
                    .user("my-user")
                    .build();

            imageModel.generate("A futuristic city at sunset", (ModelGatewayImageParameters) null);
            var parameters = parametersCaptor.getValue();

            assertEquals("opaque", parameters.background());
            assertEquals("low", parameters.moderation());
            assertEquals("webp", parameters.outputFormat());
            assertEquals("medium", parameters.quality());
            assertEquals("b64_json", parameters.responseFormat());
            assertEquals("1536x1024", parameters.size());
            assertEquals("natural", parameters.style());
            assertEquals(80, parameters.outputCompression());
            assertEquals("my-user", parameters.user());
            assertNull(parameters.n());
        });
    }

    @Test
    void should_not_support_the_image_editing() {

        withModelGatewayImageServiceMock(() -> {
            ImageModel imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .build();

            var image = Image.builder().base64Data("aGVsbG8=").build();

            assertThrows(IllegalArgumentException.class, () -> imageModel.edit(image, "Make it brighter"));
            assertThrows(IllegalArgumentException.class, () -> imageModel.edit(image, image, "Make it brighter"));
        });
    }

    @Test
    void should_map_the_exceptions_of_the_service() {

        when(mockModelGatewayImageService.generate(any(String.class), any())).thenThrow(new WatsonxException(500));

        withModelGatewayImageServiceMock(() -> {
            ImageModel imageModel = WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .apiKey("api-key")
                    .build();

            assertThrows(InternalServerException.class, () -> imageModel.generate("A futuristic city at sunset"));
        });
    }

    @Test
    void should_authenticate_with_an_authenticator_instead_of_an_api_key() {

        var authenticator = mock(IBMCloudAuthenticator.class);

        withModelGatewayImageServiceMock(() -> {
            assertDoesNotThrow(() -> WatsonxGatewayImageModel.builder()
                    .baseUrl("https://test.com")
                    .modelName("gpt-image-1")
                    .authenticator(authenticator)
                    .build());

            verify(mockModelGatewayImageServiceBuilder).authenticator(authenticator);
            verify(mockModelGatewayImageServiceBuilder, never()).apiKey(any());
        });
    }

    private void withModelGatewayImageServiceMock(Runnable action) {
        try (MockedStatic<ModelGatewayImageService> mockedStatic = mockStatic(ModelGatewayImageService.class)) {
            mockedStatic.when(ModelGatewayImageService::builder).thenReturn(mockModelGatewayImageServiceBuilder);
            action.run();
        }
    }
}
