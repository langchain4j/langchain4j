package dev.langchain4j.model.cohere;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelConstant;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link ScoringModel} that uses
 * <a href="https://docs.cohere.com/docs/rerank-guide">Cohere Rerank API</a>.
 */
public class CohereScoringModel implements ScoringModel {

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
            Proxy proxy,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.client = CohereClient.builder()
                .baseUrl(baseUrl)
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.maxRetries = getOrDefault(maxRetries, ModelConstant.DEFAULT_CLIENT_RETRIES);
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
