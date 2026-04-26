package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiImageModelName.DALL_E_2;
import static dev.langchain4j.model.openai.OpenAiImageModelName.GPT_IMAGE_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.FormDataFile;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.output.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiImageEditTest {

    private static final String DALL_E_2_RESPONSE_BODY =
            "{\"created\":1,\"data\":[{\"url\":\"https://example.com/edited.png\"}]}";
    private static final String GPT_IMAGE_RESPONSE_BODY =
            "{\"created\":1,\"data\":[{\"b64_json\":\"" + base64("edited") + "\"}]}";
    private static final String GPT_IMAGE_RESPONSE_WITH_USAGE =
            "{\"created\":1,"
                    + "\"data\":[{\"b64_json\":\"" + base64("edited") + "\"}],"
                    + "\"usage\":{"
                    + "\"input_tokens\":1039,"
                    + "\"input_tokens_details\":{\"image_tokens\":1024,\"text_tokens\":15},"
                    + "\"output_tokens\":196,"
                    + "\"output_tokens_details\":{\"image_tokens\":196,\"text_tokens\":0},"
                    + "\"total_tokens\":1235}}";

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Image pngImage(String body) {
        return Image.builder().base64Data(base64(body)).mimeType("image/png").build();
    }

    @Test
    void edit_with_dalle2_sends_single_image_part_and_default_response_format() {
        CapturingHttpClient http = new CapturingHttpClient(DALL_E_2_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, DALL_E_2.toString())
                .responseFormat("url")
                .build();

        model.edit(pngImage("input"), "make it dreamier");

        assertThat(http.captured.url()).endsWith("/images/edits");
        assertThat(http.captured.headers().get("Content-Type"))
                .containsExactly("multipart/form-data; boundary=----LangChain4j");
        assertThat(http.captured.formDataFields())
                .containsEntry("model", "dall-e-2")
                .containsEntry("prompt", "make it dreamier")
                .containsEntry("response_format", "url");
        assertThat(http.captured.formDataFiles()).containsKey("image");
        assertThat(http.captured.formDataFiles().get("image")).hasSize(1);
        FormDataFile imagePart = http.captured.formDataFiles().get("image").get(0);
        assertThat(imagePart.contentType()).isEqualTo("image/png");
        assertThat(imagePart.fileName()).isEqualTo("image-0.png");
    }

    @Test
    void edit_with_gpt_image_2_sends_repeated_image_parts_and_omits_response_format() {
        CapturingHttpClient http = new CapturingHttpClient(GPT_IMAGE_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, GPT_IMAGE_2.toString())
                .responseFormat("b64_json") // would be respected for dall-e-* but must be dropped here
                .inputFidelity("high")
                .background("transparent")
                .build();

        List<Image> inputs = List.of(pngImage("a"), pngImage("b"), pngImage("c"));
        model.edit(inputs, "merge these");

        assertThat(http.captured.formDataFields())
                .containsEntry("model", "gpt-image-2")
                .containsEntry("prompt", "merge these")
                .containsEntry("input_fidelity", "high")
                .containsEntry("background", "transparent")
                .doesNotContainKey("response_format");
        // OpenAI requires the `image[]` array-form name when more than one image is sent.
        assertThat(http.captured.formDataFiles()).doesNotContainKey("image");
        assertThat(http.captured.formDataFiles().get("image[]")).hasSize(3);
    }

    @Test
    void edit_with_mask_sends_mask_part() {
        CapturingHttpClient http = new CapturingHttpClient(DALL_E_2_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, DALL_E_2.toString()).build();

        model.edit(pngImage("input"), pngImage("mask"), "fill the masked area");

        assertThat(http.captured.formDataFiles()).containsKeys("image", "mask");
        assertThat(http.captured.formDataFiles().get("mask")).hasSize(1);
        assertThat(http.captured.formDataFiles().get("mask").get(0).fileName()).isEqualTo("mask.png");
    }

    @Test
    void edit_rejects_url_only_image_with_clear_message() {
        CapturingHttpClient http = new CapturingHttpClient(DALL_E_2_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, DALL_E_2.toString()).build();

        Image urlOnly = Image.builder().url(URI.create("https://example.com/foo.png")).build();

        assertThatThrownBy(() -> model.edit(urlOnly, "anything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("OpenAI image edit requires Image.base64Data();");
    }

    @Test
    void edit_defaults_image_mime_type_to_png_when_unset() {
        CapturingHttpClient http = new CapturingHttpClient(DALL_E_2_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, DALL_E_2.toString()).build();

        Image image = Image.builder().base64Data(base64("noMime")).build();
        model.edit(image, "go");

        FormDataFile part = http.captured.formDataFiles().get("image").get(0);
        assertThat(part.contentType()).isEqualTo("image/png");
        assertThat(part.fileName()).isEqualTo("image-0.png");
    }

    @Test
    void edit_uses_image_mime_type_when_set() {
        CapturingHttpClient http = new CapturingHttpClient(GPT_IMAGE_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, GPT_IMAGE_2.toString()).build();

        Image image = Image.builder().base64Data(base64("jpeg")).mimeType("image/jpeg").build();
        model.edit(image, "go");

        FormDataFile part = http.captured.formDataFiles().get("image").get(0);
        assertThat(part.contentType()).isEqualTo("image/jpeg");
        assertThat(part.fileName()).isEqualTo("image-0.jpg");
    }

    @Test
    void edit_with_n_includes_n_field() {
        CapturingHttpClient http = new CapturingHttpClient(DALL_E_2_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, DALL_E_2.toString()).build();

        model.edit(List.of(pngImage("x")), "go", 3);

        assertThat(http.captured.formDataFields()).containsEntry("n", "3");
    }

    @Test
    void edit_with_gpt_image_2_returns_token_usage() {
        CapturingHttpClient http = new CapturingHttpClient(GPT_IMAGE_RESPONSE_WITH_USAGE);
        OpenAiImageModel model = newModel(http, GPT_IMAGE_2.toString()).build();

        Response<Image> response = model.edit(pngImage("input"), "go");

        assertThat(response.tokenUsage()).isInstanceOf(OpenAiImageTokenUsage.class);
        OpenAiImageTokenUsage usage = (OpenAiImageTokenUsage) response.tokenUsage();
        assertThat(usage.inputTokenCount()).isEqualTo(1039);
        assertThat(usage.outputTokenCount()).isEqualTo(196);
        assertThat(usage.totalTokenCount()).isEqualTo(1235);
        assertThat(usage.inputTokensDetails().textTokens()).isEqualTo(15);
        assertThat(usage.inputTokensDetails().imageTokens()).isEqualTo(1024);
        assertThat(usage.outputTokensDetails().textTokens()).isEqualTo(0);
        assertThat(usage.outputTokensDetails().imageTokens()).isEqualTo(196);
    }

    @Test
    void edit_with_dalle2_returns_null_token_usage() {
        // dall-e responses don't include a usage block; tokenUsage() must be null (no NPE).
        CapturingHttpClient http = new CapturingHttpClient(DALL_E_2_RESPONSE_BODY);
        OpenAiImageModel model = newModel(http, DALL_E_2.toString()).build();

        Response<Image> response = model.edit(pngImage("input"), "go");

        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void multi_image_edit_returns_token_usage_on_image_list_response() {
        CapturingHttpClient http = new CapturingHttpClient(GPT_IMAGE_RESPONSE_WITH_USAGE);
        OpenAiImageModel model = newModel(http, GPT_IMAGE_2.toString()).build();

        Response<List<Image>> response = model.edit(List.of(pngImage("a"), pngImage("b")), "go", 1);

        assertThat(response.tokenUsage()).isInstanceOf(OpenAiImageTokenUsage.class);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(1235);
    }

    @Test
    void open_ai_image_token_usage_add_combines_details() {
        OpenAiImageTokenUsage a = OpenAiImageTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .inputTokensDetails(new OpenAiImageTokenUsage.TokenDetails(5, 5))
                .outputTokensDetails(new OpenAiImageTokenUsage.TokenDetails(0, 20))
                .build();
        OpenAiImageTokenUsage b = OpenAiImageTokenUsage.builder()
                .inputTokenCount(1)
                .outputTokenCount(2)
                .totalTokenCount(3)
                .inputTokensDetails(new OpenAiImageTokenUsage.TokenDetails(1, 0))
                .outputTokensDetails(new OpenAiImageTokenUsage.TokenDetails(0, 2))
                .build();

        OpenAiImageTokenUsage sum = a.add(b);

        assertThat(sum.inputTokenCount()).isEqualTo(11);
        assertThat(sum.outputTokenCount()).isEqualTo(22);
        assertThat(sum.totalTokenCount()).isEqualTo(33);
        assertThat(sum.inputTokensDetails().textTokens()).isEqualTo(6);
        assertThat(sum.inputTokensDetails().imageTokens()).isEqualTo(5);
        assertThat(sum.outputTokensDetails().textTokens()).isEqualTo(0);
        assertThat(sum.outputTokensDetails().imageTokens()).isEqualTo(22);
    }

    @Test
    void is_gpt_image_model_detection() {
        assertThat(OpenAiImageModel.isGptImageModel(null)).isFalse();
        assertThat(OpenAiImageModel.isGptImageModel("")).isFalse();
        assertThat(OpenAiImageModel.isGptImageModel("dall-e-2")).isFalse();
        assertThat(OpenAiImageModel.isGptImageModel("dall-e-3")).isFalse();
        assertThat(OpenAiImageModel.isGptImageModel("gpt-image-1")).isTrue();
        assertThat(OpenAiImageModel.isGptImageModel("gpt-image-2")).isTrue();
        assertThat(OpenAiImageModel.isGptImageModel("gpt-image-future")).isTrue();
    }

    private static OpenAiImageModel.OpenAiImageModelBuilder newModel(CapturingHttpClient http, String modelName) {
        return OpenAiImageModel.builder()
                .httpClientBuilder(new CapturingHttpClientBuilder(http))
                .baseUrl("https://example.test/v1")
                .apiKey("test-key")
                .modelName(modelName)
                .maxRetries(0);
    }

    private static final class CapturingHttpClient implements HttpClient {

        private final String responseBody;
        HttpRequest captured;

        CapturingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            this.captured = request;
            return SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .headers(Map.of("Content-Type", List.of("application/json")))
                    .body(responseBody)
                    .build();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static final class CapturingHttpClientBuilder implements HttpClientBuilder {

        private final CapturingHttpClient client;
        private Duration connectTimeout;
        private Duration readTimeout;

        CapturingHttpClientBuilder(CapturingHttpClient client) {
            this.client = client;
        }

        @Override
        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        @Override
        public Duration readTimeout() {
            return readTimeout;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        @Override
        public HttpClient build() {
            return client;
        }
    }
}
