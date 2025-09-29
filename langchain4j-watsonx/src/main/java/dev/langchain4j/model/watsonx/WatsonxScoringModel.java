package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.rerank.RerankParameters;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankResult;
import com.ibm.watsonx.ai.rerank.RerankService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link ScoringModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ScoringModel scoringModel = new WatsonxScoringModel.builder()
 *     .url("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .modelId("cross-encoder/ms-marco-minilm-l-12-v2")
 *     .build();
 * }</pre>
 *
 */
public class WatsonxScoringModel implements ScoringModel {

    private final RerankService rerankService;

    private WatsonxScoringModel(Builder builder) {
        var rerankServiceBuilder = RerankService.builder();
        if (nonNull(builder.authenticationProvider)) {
            rerankServiceBuilder.authenticationProvider(builder.authenticationProvider);
        } else {
            rerankServiceBuilder.authenticationProvider(
                    IAMAuthenticator.builder().apiKey(builder.apiKey).build());
        }

        rerankService = rerankServiceBuilder
                .url(builder.url)
                .modelId(builder.modelName)
                .version(builder.version)
                .projectId(builder.projectId)
                .spaceId(builder.spaceId)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
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

        RerankResponse response = WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> rerankService.rerank(query, inputs, parameters));

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
     * ScoringModel scoringModel = new WatsonxScoringModel.builder()
     *     .url("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .modelId("cross-encoder/ms-marco-minilm-l-12-v2")
     *     .build();
     * }</pre>
     *
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxScoringModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxBuilder<Builder> {
        private String modelName;
        private String projectId;
        private String spaceId;
        private Duration timeout;

        private Builder() {}

        public Builder url(CloudRegion cloudRegion) {
            return super.url(cloudRegion.getMlEndpoint());
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder spaceId(String spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public WatsonxScoringModel build() {
            return new WatsonxScoringModel(this);
        }
    }
}
