package dev.langchain4j.model.tei;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.tei.client.ReRankResult;
import dev.langchain4j.model.tei.client.RerankRequest;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

public class TeiScoringModel implements ScoringModel {

    private final TeiClient client;

    private final Integer maxRetries;

    @Builder
    public TeiScoringModel(String baseUrl,
                           Duration timeout,
                           Integer maxRetries) {
        this.client = TeiClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        RerankRequest request = RerankRequest.builder().query(query).texts(segments.stream().map(TextSegment::text).collect(toList())).return_text(true).build();
        List<ReRankResult> reRankResults = withRetry(() -> client.rerank(request), maxRetries);
        List<Double> scores = reRankResults.stream()
                .sorted(comparingInt(ReRankResult::getIndex))
                .map(ReRankResult::getScore)
                .collect(toList());
        return Response.from(scores);
    }
}
