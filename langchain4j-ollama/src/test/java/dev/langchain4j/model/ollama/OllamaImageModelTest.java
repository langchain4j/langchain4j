package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaImageModelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL_NAME = "x/z-image-turbo";
    private static final String IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void should_be_marked_experimental() {
        assertThat(OllamaImageModel.class).hasAnnotation(Experimental.class);
    }

    @Test
    void should_generate_image_and_send_experimental_parameters() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"model\":\"" + MODEL_NAME + "\",\"created_at\":\"2026-05-10T15:00:00Z\"," + "\"image\":\""
                        + IMAGE_BASE64 + "\",\"done\":true}"));

        OllamaImageModel model = OllamaImageModel.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .modelName(MODEL_NAME)
                .width(1024)
                .height(768)
                .steps(12)
                .seed(42)
                .build();

        Response<Image> response = model.generate("a sunset over mountains");

        assertThat(response.content().base64Data()).isEqualTo(IMAGE_BASE64);
        assertThat(response.content().mimeType()).isEqualTo("image/png");
        assertThat(response.tokenUsage()).isNull();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/generate");

        JsonNode json = OBJECT_MAPPER.readTree(request.getBody().readUtf8());
        assertThat(json.get("model").asText()).isEqualTo(MODEL_NAME);
        assertThat(json.get("prompt").asText()).isEqualTo("a sunset over mountains");
        assertThat(json.get("stream").asBoolean()).isFalse();
        assertThat(json.get("width").asInt()).isEqualTo(1024);
        assertThat(json.get("height").asInt()).isEqualTo(768);
        assertThat(json.get("steps").asInt()).isEqualTo(12);
        assertThat(json.get("options").get("seed").asInt()).isEqualTo(42);
        assertThat(json.has("seed")).isFalse();
    }

    @Test
    void should_throw_when_ollama_does_not_return_image() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"model\":\"" + MODEL_NAME
                        + "\",\"created_at\":\"2026-05-10T15:00:00Z\",\"response\":\"text\",\"done\":true}"));

        OllamaImageModel model = OllamaImageModel.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .modelName(MODEL_NAME)
                .build();

        assertThatThrownBy(() -> model.generate("a sunset over mountains"))
                .isInstanceOf(OllamaImageModel.OllamaImageGenerationException.class)
                .hasMessage("No image was returned by Ollama");
    }

    @Test
    void should_reject_dimensions_larger_than_ollama_limit() {
        assertThatThrownBy(() -> OllamaImageModel.builder()
                        .baseUrl(mockWebServer.url("/").toString())
                        .modelName(MODEL_NAME)
                        .width(4097)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("width must be between 0 and 4096, but is: 4097");

        assertThatThrownBy(() -> OllamaImageModel.builder()
                        .baseUrl(mockWebServer.url("/").toString())
                        .modelName(MODEL_NAME)
                        .height(4097)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("height must be between 0 and 4096, but is: 4097");
    }

    @Test
    void should_omit_zero_experimental_parameters_to_use_ollama_defaults() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"model\":\"" + MODEL_NAME + "\",\"created_at\":\"2026-05-10T15:00:00Z\"," + "\"image\":\""
                        + IMAGE_BASE64 + "\",\"done\":true}"));

        OllamaImageModel model = OllamaImageModel.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .modelName(MODEL_NAME)
                .width(0)
                .height(0)
                .steps(0)
                .build();

        model.generate("a sunset over mountains");

        JsonNode json =
                OBJECT_MAPPER.readTree(mockWebServer.takeRequest().getBody().readUtf8());
        assertThat(json.has("width")).isFalse();
        assertThat(json.has("height")).isFalse();
        assertThat(json.has("steps")).isFalse();
    }

    @Test
    void should_reject_negative_experimental_parameters() {
        assertThatThrownBy(() -> OllamaImageModel.builder()
                        .baseUrl(mockWebServer.url("/").toString())
                        .modelName(MODEL_NAME)
                        .width(-1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("width must be between 0 and 4096, but is: -1");

        assertThatThrownBy(() -> OllamaImageModel.builder()
                        .baseUrl(mockWebServer.url("/").toString())
                        .modelName(MODEL_NAME)
                        .height(-1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("height must be between 0 and 4096, but is: -1");

        assertThatThrownBy(() -> OllamaImageModel.builder()
                        .baseUrl(mockWebServer.url("/").toString())
                        .modelName(MODEL_NAME)
                        .steps(-1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("steps must be greater than zero, but is: -1");
    }
}
