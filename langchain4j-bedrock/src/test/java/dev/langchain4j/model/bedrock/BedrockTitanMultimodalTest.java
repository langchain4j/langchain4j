package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

class BedrockTitanMultimodalTest {

    private static final String RESPONSE = "{\"embedding\":[0.1,0.2,0.3],\"inputTextTokenCount\":4}";

    @Test
    void sends_text_and_base64_image_in_one_request() {
        AtomicReference<InvokeModelRequest> captured = new AtomicReference<>();
        BedrockTitanEmbeddingModel model = BedrockTitanEmbeddingModel.builder().model("amazon.titan-embed-image-v1")
                .client(capturingClient(captured))
                .dimensions(256)
                .build();

        EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                .input(TextContent.from("a cat"), ImageContent.from("aGVsbG8=", "image/png"))
                .build());

        String body = captured.get().body().asUtf8String();
        assertThat(captured.get().modelId()).isEqualTo("amazon.titan-embed-image-v1");
        assertThat(body).contains("inputText").contains("a cat");
        assertThat(body).contains("inputImage").contains("aGVsbG8=");
        assertThat(body).contains("outputEmbeddingLength").contains("256");

        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isEqualTo(4);
    }

    @Test
    void image_only_input_sends_just_input_image() {
        AtomicReference<InvokeModelRequest> captured = new AtomicReference<>();
        BedrockTitanEmbeddingModel model = BedrockTitanEmbeddingModel.builder().model("amazon.titan-embed-image-v1")
                .client(capturingClient(captured))
                .build();

        model.embed(EmbeddingRequest.builder()
                .input(ImageContent.from("aW1n", "image/jpeg"))
                .build());

        String body = captured.get().body().asUtf8String();
        assertThat(body).contains("inputImage").contains("aW1n");
        assertThat(body).doesNotContain("inputText");
    }

    @Test
    void supports_text_and_image_content() {
        BedrockTitanEmbeddingModel model =
                BedrockTitanEmbeddingModel.builder().model("amazon.titan-embed-image-v1").client(capturingClient(new AtomicReference<>())).build();
        assertThat(model.supportedContentTypes()).containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void rejects_image_url_because_titan_requires_base64() {
        BedrockTitanEmbeddingModel model =
                BedrockTitanEmbeddingModel.builder().model("amazon.titan-embed-image-v1").client(capturingClient(new AtomicReference<>())).build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(ImageContent.from("https://example.com/cat.png"))
                        .build()))
                .withMessageContaining("base64");
    }

    @Test
    void rejects_video_content_fail_fast() {
        BedrockTitanEmbeddingModel model =
                BedrockTitanEmbeddingModel.builder().model("amazon.titan-embed-image-v1").client(capturingClient(new AtomicReference<>())).build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(VideoContent.from("https://example.com/clip.mp4"))
                        .build()))
                .withMessageContaining("VIDEO");
    }

    @Test
    void model_name_is_exposed_to_listeners_and_response_metadata() {
        AtomicReference<String> requestModelName = new AtomicReference<>();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext ctx) {
                requestModelName.set(ctx.embeddingModel().modelName());
            }
        };

        BedrockTitanEmbeddingModel model = BedrockTitanEmbeddingModel.builder()
                .model("amazon.titan-embed-text-v2:0")
                .client(capturingClient(new AtomicReference<>()))
                .listeners(List.of(listener))
                .build();

        assertThat(model.modelName()).isEqualTo("amazon.titan-embed-text-v2:0");

        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().input("hello").build());

        // the request side now exposes the real model name to listeners (previously "unknown")
        assertThat(requestModelName.get()).isEqualTo("amazon.titan-embed-text-v2:0");
        // the response metadata carries it too
        assertThat(response.metadata().modelName()).isEqualTo("amazon.titan-embed-text-v2:0");
    }

    private static BedrockRuntimeClient capturingClient(AtomicReference<InvokeModelRequest> captured) {
        return new BedrockRuntimeClient() {
            @Override
            public InvokeModelResponse invokeModel(InvokeModelRequest request) {
                captured.set(request);
                InvokeModelResponse.Builder responseBuilder =
                        InvokeModelResponse.builder().body(SdkBytes.fromUtf8String(RESPONSE));
                responseBuilder.sdkHttpResponse(
                        SdkHttpResponse.builder().statusCode(200).build());
                return responseBuilder.build();
            }

            @Override
            public String serviceName() {
                return "bedrock-runtime";
            }

            @Override
            public void close() {}
        };
    }
}
