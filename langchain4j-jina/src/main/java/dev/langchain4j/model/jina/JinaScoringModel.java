package dev.langchain4j.model.jina;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelConstant;
import dev.langchain4j.model.jina.internal.api.JinaRerankingRequest;
import dev.langchain4j.model.jina.internal.api.JinaRerankingResponse;
import dev.langchain4j.model.jina.internal.client.JinaClient;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://jina.ai/reranker">Jina Reranker API</a>.
 */
public class JinaScoringModel implements ScoringModel {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/v1/";
    private static final String DEFAULT_MODEL = "jina-reranker-v1-base-en";

    private final JinaClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public JinaScoringModel(String baseUrl,
                            String apiKey,
                            String modelName,
                            Duration timeout,
                            Integer maxRetries,
                            Boolean logRequests,
                            Boolean logResponses) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = getOrDefault(modelName, DEFAULT_MODEL);
        this.maxRetries = getOrDefault(maxRetries, ModelConstant.DEFAULT_CLIENT_RETRIES);
    }

    public static JinaScoringModel withApiKey(String apiKey) {
        return JinaScoringModel.builder().apiKey(apiKey).build();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

        JinaRerankingRequest request = JinaRerankingRequest.builder()
                .model(modelName)
                .query(query)
                .documents(segments.stream()
                        .map(TextSegment::text)
                        .collect(toList()))
                .returnDocuments(false)  // decreasing response size, do not include text in response
                .build();

        JinaRerankingResponse response = withRetry(() -> client.rerank(request), maxRetries);

        List<Double> scores = response.results.stream()
                .sorted(comparingInt(result -> result.index))
                .map(result -> result.relevanceScore)
                .collect(toList());

        TokenUsage tokenUsage = new TokenUsage(
                response.usage.promptTokens,
                0,
                response.usage.totalTokens
        );
        return Response.from(scores, tokenUsage);
    }
}
