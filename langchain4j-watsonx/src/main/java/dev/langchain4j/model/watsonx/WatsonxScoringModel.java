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
 * RerankParameters parameters = RerankParameters.builder()
 *     .truncateInputTokens(512)
 *     .build();
 *
 * WatsonxScoreModel scoringModel = WatsonxScoreModel.builder()
 *     .service(rerankService)
 *     .parameters(rerankParameters)
 *     .build();
 * }</pre>
 *
 *
 * @see RerankService
 * @see RerankParameters
 */
public class WatsonxScoringModel implements ScoringModel {

    private final RerankService rerankService;
    private RerankParameters rerankParameters;

    protected WatsonxScoringModel(Builder builder) {
        requireNonNull(builder, "builder is required");
        this.rerankService = builder.rerankService;
        this.rerankParameters = builder.rerankParameters;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {

        if (isNull(segments) || segments.isEmpty()) return Response.from(List.of());

        if (isNull(query) || query.isBlank()) return Response.from(List.of());

        List<String> inputs = segments.stream().map(TextSegment::text).toList();

        RerankResponse response = rerankService.rerank(query, inputs, rerankParameters);

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
     * RerankParameters parameters = RerankParameters.builder()
     *     .truncateInputTokens(512)
     *     .build();
     *
     * WatsonxScoreModel scoringModel = WatsonxScoreModel.builder()
     *     .service(rerankService)
     *     .parameters(rerankParameters)
     *     .build();
     * }</pre>
     *
     *
     * @see RerankService
     * @see RerankParameters
     * @return {@link Builder} instance.
     *
     */
    public static Builder builder() {
        return new Builder();
    }

    public void setParameters(RerankParameters rerankParameters) {
        this.rerankParameters = rerankParameters;
    }

    /**
     * Builder class for constructing {@link WatsonxScoringModel} instances with configurable parameters.
     */
    public static class Builder {
        private RerankService rerankService;
        private RerankParameters rerankParameters;

        public Builder service(RerankService rerankService) {
            this.rerankService = rerankService;
            return this;
        }

        public Builder parameters(RerankParameters rerankParameters) {
            this.rerankParameters = rerankParameters;
            return this;
        }

        public WatsonxScoringModel build() {
            return new WatsonxScoringModel(this);
        }
    }
}
