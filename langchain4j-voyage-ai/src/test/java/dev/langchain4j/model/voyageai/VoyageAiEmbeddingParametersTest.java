package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VoyageAiEmbeddingParametersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEXT_MODEL = "voyage-3";
    private static final String MULTIMODAL_MODEL = "voyage-multimodal-3.5";

    private static final String RESPONSE =
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.1,0.2,0.3],\"index\":0}],"
                    + "\"model\":\"voyage-3\",\"usage\":{\"total_tokens\":7}}";

    private static final String BASE64_RESPONSE =
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":\"AACAPwAAAEA=\",\"index\":0}],"
                    + "\"model\":\"voyage-3\",\"usage\":{\"total_tokens\":7}}";

    private static MockHttpClient respondingMock() {
        return respondingMock(RESPONSE);
    }

    private static MockHttpClient respondingMock(String body) {
        return new MockHttpClient(
                SuccessfulHttpResponse.builder().statusCode(200).body(body).build());
    }

    private static VoyageAiEmbeddingModel.Builder modelBuilder(MockHttpClient mock, String modelName) {
        return VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mock))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName(modelName)
                .maxRetries(0);
    }

    private static VoyageAiEmbeddingModel model(MockHttpClient mock, String modelName) {
        return modelBuilder(mock, modelName).build();
    }

    private static JsonNode requestBody(MockHttpClient mock) throws Exception {
        return MAPPER.readTree(mock.request().body());
    }

    @Test
    void should_declare_truncation_and_encoding_format_for_a_text_model() {
        assertThat(model(respondingMock(), TEXT_MODEL).supportedParameters())
                .containsExactlyInAnyOrder(
                        EmbeddingRequestParameters.INPUT_TYPE,
                        VoyageAiEmbeddingRequestParameters.TRUNCATION,
                        VoyageAiEmbeddingRequestParameters.ENCODING_FORMAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"voyage-multimodal-3", "voyage-multimodal-3.5"})
    void should_not_declare_encoding_format_for_a_multimodal_model(String modelName) {
        assertThat(model(respondingMock(), modelName).supportedParameters())
                .containsExactlyInAnyOrder(
                        EmbeddingRequestParameters.INPUT_TYPE, VoyageAiEmbeddingRequestParameters.TRUNCATION);
    }

    @Test
    void should_reject_encoding_format_for_a_multimodal_model() {
        VoyageAiEmbeddingModel model = model(new MockHttpClient(), MULTIMODAL_MODEL);

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input("hello")
                        .parameters(VoyageAiEmbeddingRequestParameters.builder()
                                .encodingFormat("base64")
                                .build())
                        .build()))
                .withMessageContaining("voyageai.encodingFormat");
    }

    @Test
    void should_send_per_call_truncation_and_encoding_format() throws Exception {
        MockHttpClient mock = respondingMock();

        model(mock, TEXT_MODEL)
                .embed(EmbeddingRequest.builder()
                        .input("hello")
                        .parameters(VoyageAiEmbeddingRequestParameters.builder()
                                .truncation(false)
                                .encodingFormat("base64")
                                .build())
                        .build());

        assertThat(requestBody(mock).get("truncation").booleanValue()).isFalse();
        assertThat(requestBody(mock).get("encoding_format").textValue()).isEqualTo("base64");
    }

    @Test
    void should_send_per_call_truncation_on_the_multimodal_endpoint() throws Exception {
        MockHttpClient mock = respondingMock();

        model(mock, MULTIMODAL_MODEL)
                .embed(EmbeddingRequest.builder()
                        .input("hello")
                        .parameters(VoyageAiEmbeddingRequestParameters.builder()
                                .truncation(false)
                                .build())
                        .build());

        assertThat(mock.request().url()).endsWith("/multimodalembeddings");
        assertThat(requestBody(mock).get("truncation").booleanValue()).isFalse();
    }

    @Test
    void should_let_a_per_call_value_override_the_builder_default() throws Exception {
        MockHttpClient mock = respondingMock();

        modelBuilder(mock, TEXT_MODEL)
                .truncation(true)
                .encodingFormat("base64")
                .build()
                .embed(EmbeddingRequest.builder()
                        .input("hello")
                        .parameters(VoyageAiEmbeddingRequestParameters.builder()
                                .truncation(false)
                                .build())
                        .build());

        assertThat(requestBody(mock).get("truncation").booleanValue()).isFalse();
        // the parameter that was not set per call keeps the builder value
        assertThat(requestBody(mock).get("encoding_format").textValue()).isEqualTo("base64");
    }

    @Test
    void should_fall_back_to_the_builder_default_when_no_per_call_value_is_given() throws Exception {
        MockHttpClient mock = respondingMock();

        modelBuilder(mock, TEXT_MODEL)
                .truncation(true)
                .encodingFormat("base64")
                .build()
                .embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(requestBody(mock).get("truncation").booleanValue()).isTrue();
        assertThat(requestBody(mock).get("encoding_format").textValue()).isEqualTo("base64");
    }

    @Test
    void should_expose_the_builder_defaults_as_default_request_parameters() {
        VoyageAiEmbeddingRequestParameters parameters =
                (VoyageAiEmbeddingRequestParameters) modelBuilder(respondingMock(), TEXT_MODEL)
                        .truncation(true)
                        .encodingFormat("base64")
                        .build()
                        .defaultRequestParameters();

        assertThat(parameters.truncation()).isTrue();
        assertThat(parameters.encodingFormat()).isEqualTo("base64");
    }

    @Test
    void should_not_expose_encoding_format_as_a_default_for_a_multimodal_model() {
        VoyageAiEmbeddingRequestParameters parameters =
                (VoyageAiEmbeddingRequestParameters) modelBuilder(respondingMock(), MULTIMODAL_MODEL)
                        .truncation(true)
                        .encodingFormat("base64")
                        .build()
                        .defaultRequestParameters();

        assertThat(parameters.encodingFormat()).isNull();
        assertThat(parameters.presentParameters()).containsExactly(VoyageAiEmbeddingRequestParameters.TRUNCATION);
    }

    @Test
    void should_omit_truncation_and_encoding_format_when_neither_is_set() throws Exception {
        MockHttpClient mock = respondingMock();

        model(mock, TEXT_MODEL).embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(requestBody(mock).has("truncation")).isFalse();
        assertThat(requestBody(mock).has("encoding_format")).isFalse();
    }

    @Test
    void should_decode_embeddings_returned_for_a_per_call_base64_encoding_format() {
        MockHttpClient mock = respondingMock(BASE64_RESPONSE);

        EmbeddingResponse response = model(mock, TEXT_MODEL)
                .embed(EmbeddingRequest.builder()
                        .input("hello")
                        .parameters(VoyageAiEmbeddingRequestParameters.builder()
                                .encodingFormat("base64")
                                .build())
                        .build());

        assertThat(response.embeddings().get(0).vector()).containsExactly(1.0f, 2.0f);
    }

    @ParameterizedTest
    @ValueSource(strings = {TEXT_MODEL, MULTIMODAL_MODEL})
    void should_never_default_a_parameter_it_does_not_support(String modelName) {
        VoyageAiEmbeddingModel model = modelBuilder(respondingMock(), modelName)
                .truncation(true)
                .encodingFormat("base64")
                .build();

        assertThat(model.defaultRequestParameters().presentParameters()).isSubsetOf(model.supportedParameters());
    }

    @Test
    void should_override_only_the_parameters_that_are_set() {
        VoyageAiEmbeddingRequestParameters base = VoyageAiEmbeddingRequestParameters.builder()
                .truncation(true)
                .encodingFormat("base64")
                .build();

        VoyageAiEmbeddingRequestParameters merged = base.overrideWith(
                VoyageAiEmbeddingRequestParameters.builder().truncation(false).build());

        assertThat(merged.truncation()).isFalse();
        assertThat(merged.encodingFormat()).isEqualTo("base64");
    }
}
