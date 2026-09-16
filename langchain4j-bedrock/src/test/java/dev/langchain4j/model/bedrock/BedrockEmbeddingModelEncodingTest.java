package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Start the test JVM with {@code -Dfile.encoding=windows-1252} to exercise the non-UTF-8 default
 * charset reported in #1209.
 */
class BedrockEmbeddingModelEncodingTest {

    private static final String TEXT = "Hello, olá, 你好, 😀";

    @ParameterizedTest
    @ValueSource(
            strings = {"amazon.titan-embed-text-v1", "amazon.titan-embed-text-v2:0", "amazon.titan-embed-image-v1"})
    void should_encode_titan_request_as_utf8(String modelId) {
        BedrockRuntimeClient client = clientReturning("{\"embedding\":[0.1,0.2],\"inputTextTokenCount\":4}");
        BedrockTitanEmbeddingModel model = BedrockTitanEmbeddingModel.builder()
                .model(modelId)
                .client(client)
                .build();

        model.embed(TEXT);

        assertThat(requestBody(client).get("inputText")).isEqualTo(TEXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cohere.embed-english-v3", "cohere.embed-multilingual-v3"})
    void should_encode_cohere_request_as_utf8(String modelId) {
        BedrockRuntimeClient client = clientReturning("{\"embeddings\":{\"float\":[[0.1,0.2]]}}");
        BedrockCohereEmbeddingModel model = BedrockCohereEmbeddingModel.builder()
                .model(modelId)
                .inputType(SEARCH_QUERY)
                .client(client)
                .build();

        model.embed(TEXT);

        assertThat(requestBody(client).get("texts")).isEqualTo(List.of(TEXT));
    }

    private static BedrockRuntimeClient clientReturning(String body) {
        InvokeModelResponse.Builder responseBuilder =
                InvokeModelResponse.builder().body(SdkBytes.fromUtf8String(body));
        responseBuilder.sdkHttpResponse(
                SdkHttpResponse.builder().statusCode(200).build());
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(responseBuilder.build());
        return client;
    }

    private static Map<?, ?> requestBody(BedrockRuntimeClient client) {
        ArgumentCaptor<InvokeModelRequest> captor = ArgumentCaptor.forClass(InvokeModelRequest.class);
        verify(client).invokeModel(captor.capture());
        return Json.fromJson(captor.getValue().body().asUtf8String(), Map.class);
    }
}
