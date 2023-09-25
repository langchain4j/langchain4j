package dev.langchain4j.model.huggingface;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2;
import static java.util.stream.Collectors.toList;

public class HuggingFaceEmbeddingModel implements EmbeddingModel {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final HuggingFaceClient client;
    private final boolean waitForModel;

    @Builder
    public HuggingFaceEmbeddingModel(String accessToken, String modelId, Boolean waitForModel, Duration timeout) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
        }
        this.client = new HuggingFaceClient(
                accessToken,
                modelId == null ? SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2 : modelId,
                timeout == null ? DEFAULT_TIMEOUT : timeout
        );
        this.waitForModel = waitForModel == null ? true : waitForModel;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = new EmbeddingRequest(texts, waitForModel);

        List<float[]> response = client.embed(request);

        List<Embedding> embeddings = response.stream()
                .map(Embedding::from)
                .collect(toList());

        return Response.from(embeddings);
    }

    public static HuggingFaceEmbeddingModel withAccessToken(String accessToken) {
        return builder().accessToken(accessToken).build();
    }
}