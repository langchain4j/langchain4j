package dev.langchain4j.model.huggingface;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Result;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class HuggingFaceEmbeddingModel implements EmbeddingModel {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final HuggingFaceClient client;
    private final boolean waitForModel;

    @Builder
    public HuggingFaceEmbeddingModel(String accessToken, String modelId, Boolean waitForModel, Duration timeout) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
        }
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalArgumentException("HuggingFace model id must be defined. For example: \"sentence-transformers/all-MiniLM-L6-v2\"");
        }
        this.client = new HuggingFaceClient(accessToken, modelId, timeout == null ? DEFAULT_TIMEOUT : timeout);
        this.waitForModel = waitForModel == null ? true : waitForModel;
    }

    @Override
    public Result<Embedding> embed(String text) {
        Result<List<Embedding>> result = embedTexts(singletonList(text));
        return new Result<>(result.get().get(0));
    }

    @Override
    public Result<Embedding> embed(DocumentSegment documentSegment) {
        return embed(documentSegment.text());
    }

    @Override
    public Result<List<Embedding>> embedAll(List<DocumentSegment> documentSegments) {

        List<String> texts = documentSegments.stream()
                .map(DocumentSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private Result<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = new EmbeddingRequest(texts, waitForModel);

        List<float[]> response = client.embed(request);

        List<Embedding> embeddings = response.stream()
                .map(Embedding::from)
                .collect(toList());

        return new Result<>(embeddings);
    }
}