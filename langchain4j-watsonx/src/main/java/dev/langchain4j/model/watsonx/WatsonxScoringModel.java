package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import com.ibm.watsonx.ai.rerank.RerankParameters;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankResult;
import com.ibm.watsonx.ai.rerank.RerankService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link ScoringModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * RerankService rerankService = RerankService.builder()
 *     .url("https://...") // or use CloudRegion
 *     .authenticationProvider(authProvider)
 *     .projectId("my-project-id")
 *     .modelId("cross-encoder/ms-marco-minilm-l-12-v2")
 *     .build();
 *
 * WatsonxScoringModel scoringModel = WatsonxScoringModel.builder()
 *     .service(rerankService)
 *     .build();
 * }</pre>
 *
 * @see RerankService
 */
public class WatsonxScoringModel implements ScoringModel {

    private final RerankService rerankService;

    private WatsonxScoringModel(Builder builder) {
        requireNonNull(builder, "builder is required");
        this.rerankService = builder.rerankService;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        return scoreAll(segments, query, null);
    }

    /**
     * Scores all provided {@link TextSegment}s against a given query using the given {@link RerankParameters}.
     *
     * @param segments The list of {@link TextSegment}s to score.
     * @param query The query against which to score the segments.
     * @param parameters the rerank parameters to use.
     * @return the list of scores. The order of scores corresponds to the order of {@link TextSegment}s.
     */
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query, RerankParameters parameters) {

        if (isNull(segments) || segments.isEmpty()) return Response.from(List.of());

        if (isNull(query) || query.isBlank()) return Response.from(List.of());

        List<String> inputs = segments.stream().map(TextSegment::text).toList();

        RerankResponse response = rerankService.rerank(query, inputs, parameters);

        var content = new Double[response.results().size()];
        for (RerankResult rerankResult : response.results()) content[rerankResult.index()] = rerankResult.score();

        return Response.from(Arrays.asList(content), new TokenUsage(response.inputTokenCount()));
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * RerankService rerankService = RerankService.builder()
     *     .url("https://...") // or use CloudRegion
     *     .authenticationProvider(authProvider)
     *     .projectId("my-project-id")
     *     .modelId("cross-encoder/ms-marco-minilm-l-12-v2")
     *     .build();
     *
     * WatsonxScoringModel scoringModel = WatsonxScoringModel.builder()
     *     .service(rerankService)
     *     .build();
     * }</pre>
     *
     * @see RerankService
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxScoringModel} instances with configurable parameters.
     */
    public static class Builder {
        private RerankService rerankService;

        public Builder service(RerankService rerankService) {
            this.rerankService = rerankService;
            return this;
        }

        public WatsonxScoringModel build() {
            return new WatsonxScoringModel(this);
        }
    }
}
