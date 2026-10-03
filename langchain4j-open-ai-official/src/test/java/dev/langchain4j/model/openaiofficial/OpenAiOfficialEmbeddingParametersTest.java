package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiOfficialEmbeddingParametersTest {

    private static final String MODEL_NAME = "text-embedding-3-small";
    private static final String EMBEDDINGS_PATH = "embeddings";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OpenAiOfficialStubHttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new OpenAiOfficialStubHttpClient();
    }

    private OpenAiOfficialEmbeddingModel.Builder modelBuilder() {
        return OpenAiOfficialEmbeddingModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .modelName(MODEL_NAME);
    }

    private void enqueueFloatResponse() {
        httpClient.enqueue(EMBEDDINGS_PATH, """
                {
                  "object": "list",
                  "model": "text-embedding-3-small",
                  "data": [{"object": "embedding", "index": 0, "embedding": [0.5, -0.25]}],
                  "usage": {"prompt_tokens": 3, "total_tokens": 3}
                }
                """);
    }

    private JsonNode requestBody() throws Exception {
        return OBJECT_MAPPER.readTree(httpClient.requestTo(EMBEDDINGS_PATH).body());
    }

    private static EmbeddingRequest requestOf(String text, EmbeddingRequestParameters parameters) {
        return EmbeddingRequest.builder().input(text).parameters(parameters).build();
    }

    @Test
    void should_send_the_builder_values_as_defaults() throws Exception {
        enqueueFloatResponse();

        modelBuilder()
                .user("user-1")
                .dimensions(2)
                .encodingFormat("base64")
                .customParameters(Map.of("input_type", "query"))
                .build()
                .embed(EmbeddingRequest.builder().input("hello").build());

        JsonNode body = requestBody();
        assertThat(body.get("user").textValue()).isEqualTo("user-1");
        assertThat(body.get("dimensions").intValue()).isEqualTo(2);
        assertThat(body.get("encoding_format").textValue()).isEqualTo("base64");
        assertThat(body.get("input_type").textValue()).isEqualTo("query");
    }

    @Test
    void should_let_a_per_call_value_override_the_builder_default() throws Exception {
        enqueueFloatResponse();

        modelBuilder()
                .user("builder-user")
                .encodingFormat("base64")
                .build()
                .embed(requestOf(
                        "hello",
                        OpenAiOfficialEmbeddingRequestParameters.builder()
                                .user("per-call-user")
                                .build()));

        JsonNode body = requestBody();
        assertThat(body.get("user").textValue()).isEqualTo("per-call-user");
        assertThat(body.get("encoding_format").textValue()).isEqualTo("base64");
    }

    @Test
    void should_send_encoding_format_set_per_call() throws Exception {
        enqueueFloatResponse();

        modelBuilder()
                .build()
                .embed(requestOf(
                        "hello",
                        OpenAiOfficialEmbeddingRequestParameters.builder()
                                .encodingFormat("float")
                                .build()));

        assertThat(requestBody().get("encoding_format").textValue()).isEqualTo("float");
    }

    @Test
    void should_omit_unset_parameters_and_keep_the_sdk_default_encoding_format() throws Exception {
        enqueueFloatResponse();

        modelBuilder().build().embed(EmbeddingRequest.builder().input("hello").build());

        JsonNode body = requestBody();
        assertThat(body.has("user")).isFalse();
        assertThat(body.has("dimensions")).isFalse();
        assertThat(body.get("encoding_format").textValue()).isEqualTo("base64");
    }

    @Test
    void should_let_a_per_call_dimensions_override_the_builder_default() throws Exception {
        enqueueFloatResponse();

        modelBuilder()
                .dimensions(1536)
                .build()
                .embed(requestOf(
                        "hello",
                        OpenAiOfficialEmbeddingRequestParameters.builder()
                                .dimensions(256)
                                .build()));

        assertThat(requestBody().get("dimensions").intValue()).isEqualTo(256);
    }

    @Test
    void should_let_per_call_custom_parameters_override_the_builder_default() throws Exception {
        enqueueFloatResponse();

        modelBuilder()
                .customParameters(Map.of("input_type", "document"))
                .build()
                .embed(requestOf(
                        "hello",
                        OpenAiOfficialEmbeddingRequestParameters.builder()
                                .customParameter("input_type", "query")
                                .build()));

        assertThat(requestBody().get("input_type").textValue()).isEqualTo("query");
    }

    @Test
    void should_apply_the_parameters_to_every_batch() throws Exception {
        enqueueFloatResponse();
        enqueueFloatResponse();

        modelBuilder()
                .maxSegmentsPerBatch(1)
                .build()
                .embed(EmbeddingRequest.builder()
                        .inputs(List.of(EmbeddingInput.from("hello"), EmbeddingInput.from("world")))
                        .parameters(OpenAiOfficialEmbeddingRequestParameters.builder()
                                .user("user-1")
                                .build())
                        .build());

        List<OpenAiOfficialStubHttpClient.RecordedRequest> requests = httpClient.recordedRequests();
        assertThat(requests).hasSize(2);
        for (OpenAiOfficialStubHttpClient.RecordedRequest request : requests) {
            assertThat(OBJECT_MAPPER.readTree(request.body()).get("user").textValue())
                    .isEqualTo("user-1");
        }
    }

    @Test
    void should_decode_base64_embeddings_into_the_same_floats() {
        httpClient.enqueue(EMBEDDINGS_PATH, """
                {
                  "object": "list",
                  "model": "text-embedding-3-small",
                  "data": [{"object": "embedding", "index": 0, "embedding": "%s"}],
                  "usage": {"prompt_tokens": 3, "total_tokens": 3}
                }
                """.formatted(base64Of(0.5f, -0.25f)));

        EmbeddingResponse response = modelBuilder()
                .build()
                .embed(requestOf(
                        "hello",
                        OpenAiOfficialEmbeddingRequestParameters.builder()
                                .encodingFormat("base64")
                                .build()));

        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.5f, -0.25f);
    }

    @Test
    void should_report_the_model_and_token_usage_from_the_response() {
        enqueueFloatResponse();

        EmbeddingResponse response = modelBuilder()
                .build()
                .embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(response.metadata().modelName()).isEqualTo(MODEL_NAME);
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isEqualTo(3);
    }

    @Test
    void should_reject_a_parameter_it_does_not_support() {
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> modelBuilder()
                        .build()
                        .embed(requestOf(
                                "hello",
                                OpenAiOfficialEmbeddingRequestParameters.builder()
                                        .inputType(EmbeddingInputType.QUERY)
                                        .build())))
                .withMessageContaining("inputType");
    }

    @Test
    void should_never_default_a_parameter_it_does_not_support() {
        var model = modelBuilder()
                .user("user-1")
                .encodingFormat("base64")
                .customParameters(Map.of("input_type", "query"))
                .build();

        assertThat(model.defaultRequestParameters().presentParameters()).isSubsetOf(model.supportedParameters());
    }

    @Test
    void should_keep_embedding_all_text_segments_without_per_call_parameters() {
        enqueueFloatResponse();

        var embeddings = modelBuilder()
                .user("user-1")
                .build()
                .embedAll(List.of(TextSegment.from("hello")))
                .content();

        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0).vector()).containsExactly(0.5f, -0.25f);
    }

    private static String base64Of(float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}
