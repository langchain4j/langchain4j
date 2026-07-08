package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bedrock Amazon Titan embedding model with support for both versions:
 * {@code amazon.titan-embed-text-v1} and {@code amazon.titan-embed-text-v2:0}
 * <br>
 * See more details <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html">here</a> and
 * <a href="https://aws.amazon.com/blogs/aws/amazon-titan-text-v2-now-available-in-amazon-bedrock-optimized-for-improving-rag/">here</a>.
 */
public class BedrockTitanEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockTitanEmbeddingResponse> {

    private final String model;
    private final Integer dimensions;
    private final Boolean normalize;

    protected BedrockTitanEmbeddingModel(BedrockTitanEmbeddingModelBuilder<?, ?> builder) {
        super(builder);
        this.model = ensureNotBlank(builder.model, "model");
        this.dimensions = builder.dimensions;
        this.normalize = builder.normalize;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Integer knownDimension() {
        return dimensions;
    }

    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {
        if (isMultimodal()) {
            List<Map<String, Object>> bodies = new ArrayList<>();
            for (TextSegment textSegment : textSegments) {
                Map<String, Object> body = new HashMap<>();
                body.put("inputText", textSegment.text());
                if (dimensions != null) {
                    body.put("embeddingConfig", of("outputEmbeddingLength", dimensions));
                }
                bodies.add(body);
            }
            return bodies;
        }
        if (Types.TitanEmbedTextV1.getValue().equals(this.model)) {
            if (this.dimensions != null || this.normalize != null) {
                throw new IllegalArgumentException(
                        "Dimensions and normalize are not supported for Titan Embedding model V1");
            }
            return textSegments.stream()
                    .map(TextSegment::text)
                    .map(text -> of("inputText", text))
                    .collect(Collectors.toList());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (TextSegment textSegment : textSegments) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("inputText", textSegment.text());
            parameters.put("dimensions", dimensions);
            parameters.put("normalize", normalize);
            result.add(parameters);
        }
        return result;
    }

    @Override
    protected Class<BedrockTitanEmbeddingResponse> getResponseClassType() {
        return BedrockTitanEmbeddingResponse.class;
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return isMultimodal() ? Set.of(ContentType.TEXT, ContentType.IMAGE) : Set.of(ContentType.TEXT);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {

        if (!isMultimodal()) {
            // text-only Titan models: reuse the legacy text path
            Response<List<Embedding>> legacy = embedAll(request.inputs().stream()
                    .map(input -> TextSegment.from(input.text()))
                    .collect(Collectors.toList()));
            return EmbeddingResponse.builder()
                    .embeddings(legacy.content())
                    .metadata(EmbeddingResponseMetadata.builder()
                            .modelName(model)
                            .tokenUsage(legacy.tokenUsage())
                            .build())
                    .build();
        }

        // multimodal (amazon.titan-embed-image-v1): text and/or one image per input -> one fused vector
        List<Embedding> embeddings = new ArrayList<>();
        int totalTokens = 0;
        for (EmbeddingInput input : request.inputs()) {
            String text = input.text();
            Map<String, Object> body = new HashMap<>();
            if (!text.isEmpty()) {
                body.put("inputText", text);
            }
            String imageBase64 = extractImageBase64(input);
            if (imageBase64 != null) {
                body.put("inputImage", imageBase64);
            }
            if (dimensions != null) {
                body.put("embeddingConfig", of("outputEmbeddingLength", dimensions));
            }

            BedrockTitanEmbeddingResponse response = Json.fromJson(
                    withRetryMappingExceptions(
                                    () -> invoke(Json.toJson(body)),
                                    getMaxRetries(),
                                    BedrockExceptionMapper.INSTANCE)
                            .body()
                            .asUtf8String(),
                    BedrockTitanEmbeddingResponse.class);
            embeddings.add(response.toEmbedding());
            totalTokens += response.getInputTextTokenCount();
        }

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(model)
                        .tokenUsage(new TokenUsage(totalTokens))
                        .build())
                .build();
    }

    private boolean isMultimodal() {
        return Types.TitanEmbedImageV1.getValue().equals(model) || model.contains("embed-image");
    }

    private String extractImageBase64(EmbeddingInput input) {
        String base64 = null;
        for (Content content : input.contents()) {
            if (content instanceof ImageContent imageContent) {
                if (base64 != null) {
                    throw new UnsupportedFeatureException("Amazon Titan supports at most one image per input");
                }
                if (imageContent.image().base64Data() == null) {
                    throw new UnsupportedFeatureException(
                            "Amazon Titan requires base64 image data (a URL is not supported)");
                }
                base64 = imageContent.image().base64Data();
            }
        }
        return base64;
    }

    public enum Types {
        TitanEmbedTextV1("amazon.titan-embed-text-v1"),
        TitanEmbedTextV2("amazon.titan-embed-text-v2:0"),
        TitanEmbedImageV1("amazon.titan-embed-image-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

        public String getValue() {
            return value;
        }
    }

    public String getModel() {
        return model;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public Boolean getNormalize() {
        return normalize;
    }

    public static BedrockTitanEmbeddingModelBuilder<?, ?> builder() {
        return new BedrockTitanEmbeddingModelBuilderImpl();
    }

    public abstract static class BedrockTitanEmbeddingModelBuilder<
                    C extends BedrockTitanEmbeddingModel, B extends BedrockTitanEmbeddingModelBuilder<C, B>>
            extends AbstractBedrockEmbeddingModel.AbstractBedrockEmbeddingModelBuilder<
                    BedrockTitanEmbeddingResponse, C, B> {

        private String model;
        private Integer dimensions;
        private Boolean normalize;

        public B model(String model) {
            this.model = model;
            return self();
        }

        public B dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return self();
        }

        public B normalize(Boolean normalize) {
            this.normalize = normalize;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }

    private static final class BedrockTitanEmbeddingModelBuilderImpl
            extends BedrockTitanEmbeddingModelBuilder<
                    BedrockTitanEmbeddingModel, BedrockTitanEmbeddingModelBuilderImpl> {
        protected BedrockTitanEmbeddingModelBuilderImpl self() {
            return this;
        }

        public BedrockTitanEmbeddingModel build() {
            return new BedrockTitanEmbeddingModel(this);
        }
    }
}
