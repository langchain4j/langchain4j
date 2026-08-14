package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY;
import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.Model.COHERE_EMBED_MULTILINGUAL_V3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

class BedrockCohereEmbeddingModelTest {

    @Test
    void should_return_token_usage_from_bedrock_input_token_count_header() {
        Queue<InvokeModelResponse> responses =
                new ArrayDeque<>(List.of(invokeModelResponse("[0.1,0.2]", "3"), invokeModelResponse("[0.3,0.4]", "4")));

        BedrockCohereEmbeddingModel model = BedrockCohereEmbeddingModel.builder()
                .client(clientReturning(responses))
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .maxSegmentsPerBatch(1)
                .build();

        Response<List<Embedding>> response =
                model.embedAll(List.of(TextSegment.from("first"), TextSegment.from("second")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.content())
                .allSatisfy(embedding -> assertThat(embedding.vector()).hasSize(2));
        assertThat(responses).isEmpty();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(7);
    }

    @Test
    void should_not_return_token_usage_when_bedrock_does_not_provide_token_count() {
        Queue<InvokeModelResponse> responses =
                new ArrayDeque<>(List.of(invokeModelResponseWithoutTokenCount("[0.1,0.2]")));

        BedrockCohereEmbeddingModel model = BedrockCohereEmbeddingModel.builder()
                .client(clientReturning(responses))
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .build();

        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("first")));

        assertThat(response.content()).hasSize(1);
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_fall_back_to_body_token_count_when_header_is_missing() {
        Queue<InvokeModelResponse> responses =
                new ArrayDeque<>(List.of(invokeModelResponseWithBodyTokenCount("[0.1,0.2]", 5)));

        BedrockCohereEmbeddingModel model = BedrockCohereEmbeddingModel.builder()
                .client(clientReturning(responses))
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .build();

        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("first")));

        assertThat(response.content()).hasSize(1);
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(5);
        assertThat(response.tokenUsage().outputTokenCount()).isNull();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(5);
    }

    @Test
    void should_report_amazon_bedrock_as_provider() {
        BedrockCohereEmbeddingModel model = BedrockCohereEmbeddingModel.builder()
                .client(clientReturning(new ArrayDeque<>()))
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .build();

        assertThat(model.provider()).isEqualTo(ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_report_model_name_in_response_metadata() {
        Queue<InvokeModelResponse> responses = new ArrayDeque<>(List.of(invokeModelResponse("[0.1,0.2]", "3")));

        BedrockCohereEmbeddingModel model = BedrockCohereEmbeddingModel.builder()
                .client(clientReturning(responses))
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .build();

        assertThat(model.modelName()).isEqualTo("cohere.embed-multilingual-v3");

        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(response.metadata().modelName()).isEqualTo("cohere.embed-multilingual-v3");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_reject_non_positive_max_segments_per_batch(int maxSegmentsPerBatch) {
        assertThatThrownBy(() -> modelWithMaxSegmentsPerBatch(maxSegmentsPerBatch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxSegmentsPerBatch must be greater than zero, but is: " + maxSegmentsPerBatch);
    }

    @Test
    void should_accept_positive_max_segments_per_batch() {
        assertThatNoException().isThrownBy(() -> modelWithMaxSegmentsPerBatch(1));
    }

    private static BedrockCohereEmbeddingModel modelWithMaxSegmentsPerBatch(int maxSegmentsPerBatch) {
        return BedrockCohereEmbeddingModel.builder()
                .client(clientReturning(new ArrayDeque<>()))
                .model(COHERE_EMBED_MULTILINGUAL_V3)
                .inputType(SEARCH_QUERY)
                .maxSegmentsPerBatch(maxSegmentsPerBatch)
                .build();
    }

    private static BedrockRuntimeClient clientReturning(Queue<InvokeModelResponse> responses) {
        return new BedrockRuntimeClient() {

            @Override
            public InvokeModelResponse invokeModel(InvokeModelRequest request) {
                return responses.remove();
            }

            @Override
            public String serviceName() {
                return "bedrock-runtime";
            }

            @Override
            public void close() {}
        };
    }

    private static InvokeModelResponse invokeModelResponse(String vector, String inputTokenCount) {
        InvokeModelResponse.Builder responseBuilder = InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"embeddings\":{\"float\":[" + vector + "]}}"));
        responseBuilder.sdkHttpResponse(SdkHttpResponse.builder()
                .statusCode(200)
                .putHeader("x-amzn-bedrock-input-token-count", inputTokenCount)
                .build());
        return responseBuilder.build();
    }

    private static InvokeModelResponse invokeModelResponseWithoutTokenCount(String vector) {
        InvokeModelResponse.Builder responseBuilder = InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"embeddings\":{\"float\":[" + vector + "]}}"));
        responseBuilder.sdkHttpResponse(
                SdkHttpResponse.builder().statusCode(200).build());
        return responseBuilder.build();
    }

    private static InvokeModelResponse invokeModelResponseWithBodyTokenCount(String vector, int inputTokenCount) {
        InvokeModelResponse.Builder responseBuilder = InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"embeddings\":{\"float\":[" + vector + "]},\"inputTextTokenCount\":"
                        + inputTokenCount + "}"));
        responseBuilder.sdkHttpResponse(
                SdkHttpResponse.builder().statusCode(200).build());
        return responseBuilder.build();
    }
}
