package dev.langchain4j.model.jinaAi.rerank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://jina.ai/reranker">Jina Rerank API</a>.
 */
public class JinaScoringModel implements ScoringModel {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/v1/";
    /**
     * This is the leading Jina Reranker model
     */
    private static final String DEFAULT_MODEL = "jina-reranker-v1-base-en";

    private final JinaClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public JinaScoringModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, DEFAULT_MODEL);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public static JinaScoringModel withApiKey(String apiKey) {
        return JinaScoringModel.builder().apiKey(apiKey).build();
    }



    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

        RerankRequest request = RerankRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream()
                        .map(TextSegment::text)
                        .collect(toList()))
                .build();

        RerankResponse response = withRetry(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.getResults().stream()
                .sorted(comparingInt(Result::getIndex))
                .map(Result::getRelevanceScore)
                .collect(toList());

        return Response.from(scores, new TokenUsage(response.getUsage().getTotalTokens()));
    }
}
