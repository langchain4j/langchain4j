package dev.langchain4j.model.huggingface;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.spi.HuggingFaceClientFactory;
import dev.langchain4j.model.huggingface.spi.HuggingFaceEmbeddingModelBuilderFactory;
import dev.langchain4j.model.huggingface.util.UrlUtil;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

public class HuggingFaceEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private HuggingFaceClient client;
    private final boolean waitForModel;
    private final String modelId;
    private final String baseUrl;

    /**
     * Constructor with Custom baseUrl parameter
     */
    @Builder
    public HuggingFaceEmbeddingModel(
            String baseUrl, String accessToken, String modelId, Boolean waitForModel, Duration timeout) {

        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
        }
        this.waitForModel = waitForModel == null || waitForModel;
        this.baseUrl = baseUrl;
        this.modelId = modelId;
        this.client = createClient(accessToken, modelId, timeout);
    }

    @Builder
    public HuggingFaceEmbeddingModel(String accessToken, String modelId, Boolean waitForModel, Duration timeout) {
        this(null, accessToken, modelId, waitForModel, timeout);
    }

    private HuggingFaceClient createClient(String accessToken, String modelId, Duration timeout) {
        if (!Objects.isNull(baseUrl) && UrlUtil.isNotValidUrl(baseUrl)) {
            throw new IllegalArgumentException("Invalid url: " + baseUrl);
        }

        return FactoryCreator.FACTORY.create(new HuggingFaceClientFactory.Input() {
            @Override
            public String baseUrl() {
                return baseUrl;
            }

            @Override
            public String apiKey() {
                return accessToken;
            }

            @Override
            public String modelId() {
                return modelId == null ? SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2 : modelId;
            }

            @Override
            public Duration timeout() {
                return timeout == null ? DEFAULT_TIMEOUT : timeout;
            }
        });
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = new EmbeddingRequest(texts, waitForModel);

        List<float[]> response = client.embed(request);

        List<Embedding> embeddings = response.stream().map(Embedding::from).collect(toList());

        return Response.from(embeddings);
    }

    public static HuggingFaceEmbeddingModel withAccessToken(String accessToken) {
        return builder().accessToken(accessToken).build();
    }

    public static HuggingFaceEmbeddingModelBuilder builder() {
        for (HuggingFaceEmbeddingModelBuilderFactory factory :
                loadFactories(HuggingFaceEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new HuggingFaceEmbeddingModelBuilder();
    }

    public static class HuggingFaceEmbeddingModelBuilder {
        public HuggingFaceEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
