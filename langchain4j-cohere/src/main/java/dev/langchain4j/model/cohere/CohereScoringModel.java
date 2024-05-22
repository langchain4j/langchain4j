package dev.langchain4j.model.cohere;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.cohere.internal.api.RerankRequest;
import dev.langchain4j.model.cohere.internal.api.RerankResponse;
import dev.langchain4j.model.cohere.internal.api.Result;
import dev.langchain4j.model.cohere.internal.client.CohereClient;
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
 * <a href="https://docs.cohere.com/docs/rerank-guide">Cohere Rerank API</a>.
 */
public class CohereScoringModel implements ScoringModel {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";

    private final CohereClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public CohereScoringModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = modelName;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public static CohereScoringModel withApiKey(String apiKey) {
        return CohereScoringModel.builder().apiKey(apiKey).build();
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

        return Response.from(scores, new TokenUsage(response.getMeta().getBilledUnits().getSearchUnits()));
    }
}
