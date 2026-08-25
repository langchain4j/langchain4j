package dev.langchain4j.model.jina;

import static dev.langchain4j.model.embedding.request.EmbeddingInputType.DOCUMENT;
import static dev.langchain4j.model.embedding.request.EmbeddingInputType.QUERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import org.junit.jupiter.api.Test;

class JinaEmbeddingParametersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JinaEmbeddingModel model(String modelName) {
        return JinaEmbeddingModel.builder()
                .apiKey("test-key")
                .modelName(modelName)
                .build();
    }

    private static EmbeddingRequest request(EmbeddingInputType inputType) {
        return EmbeddingRequest.builder().input("a query").inputType(inputType).build();
    }

    private static String requestJson(String modelName, EmbeddingInputType inputType) throws Exception {
        return MAPPER.writeValueAsString(model(modelName).buildRequest(request(inputType)));
    }

    private static String multimodalRequestJson(String modelName, EmbeddingInputType inputType) throws Exception {
        return MAPPER.writeValueAsString(model(modelName).buildMultimodalRequest(request(inputType)));
    }

    @Test
    void should_declare_input_type_as_supported_parameter() {
        assertThat(model("jina-embeddings-v3").supportedParameters())
                .containsExactly(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Test
    void should_map_query_to_retrieval_query_task() throws Exception {
        assertThat(requestJson("jina-embeddings-v3", QUERY)).contains("\"task\":\"retrieval.query\"");
    }

    @Test
    void should_map_document_to_retrieval_passage_task() throws Exception {
        assertThat(requestJson("jina-embeddings-v3", DOCUMENT)).contains("\"task\":\"retrieval.passage\"");
    }

    @Test
    void should_omit_task_when_no_input_type_is_requested() throws Exception {
        assertThat(requestJson("jina-embeddings-v3", null)).doesNotContain("task");
    }

    @Test
    void should_send_task_for_multimodal_model() throws Exception {
        // jina-embeddings-v4 is both multimodal and task-aware, so the multimodal path must carry the task too
        assertThat(multimodalRequestJson("jina-embeddings-v4", QUERY)).contains("\"task\":\"retrieval.query\"");
    }

    @Test
    void should_omit_task_for_multimodal_model_when_no_input_type_is_requested() throws Exception {
        assertThat(multimodalRequestJson("jina-embeddings-v4", null)).doesNotContain("task");
    }

    @Test
    void should_map_null_input_type_to_null_task() {
        assertThat(JinaEmbeddingModel.toJinaTask(null)).isNull();
    }

    @Test
    void should_declare_input_type_for_task_aware_multimodal_model() {
        assertThat(model("jina-embeddings-v4").supportedParameters())
                .containsExactly(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Test
    void should_not_declare_input_type_for_models_without_task_support() {
        assertThat(model("jina-clip-v2").supportedParameters()).isEmpty();
        assertThat(model("jina-clip-v1").supportedParameters()).isEmpty();
        assertThat(model("jina-embeddings-v2-base-en").supportedParameters()).isEmpty();
    }

    @Test
    void should_reject_input_type_for_model_without_task_support() {
        // mirrors AbstractEmbeddingModelIT#should_fail_when_input_type_is_not_supported, which the common
        // JinaEmbeddingModelIT runs against jina-clip-v2; baseUrl points at a dead port so that any attempt
        // to reach Jina surfaces as something other than UnsupportedFeatureException
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .baseUrl("http://127.0.0.1:1/")
                .apiKey("test-key")
                .modelName("jina-clip-v2")
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input("hello")
                        .inputType(QUERY)
                        .build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class);
    }
}
