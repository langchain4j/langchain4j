package dev.langchain4j.model.jinaAi;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

public class JinaAiEmbeddingModel implements EmbeddingModel {

    /**
     * <a href="https://api.jina.ai/redoc#tag/embeddings">Jina API reference</a>
     */
    private final JinaAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public JinaAiEmbeddingModel(String baseUrl,
                                String modelName,
                                Duration timeout,
                                Integer maxRetries) {
        this.client = JinaAiClient.builder()
//                .baseUrl(baseUrl)
//                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 3);
    }


    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        // TODO ADD IMPLEMETATION HERE
        return Response.from(embeddings);
    }

}
