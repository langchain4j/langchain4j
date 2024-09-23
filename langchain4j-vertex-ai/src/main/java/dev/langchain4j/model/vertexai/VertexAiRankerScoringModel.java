package dev.langchain4j.model.vertexai;

import com.google.cloud.discoveryengine.v1beta.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Implementation of a <code>ScoringModel</code> for the Vertex AI Ranking API:
 * https://cloud.google.com/generative-ai-app-builder/docs/ranking
 */
public class VertexAiRankerScoringModel implements ScoringModel {

    private final String model;
    private final String projectId;
    private final String projectNum;
    private final String location;
    private final String titleMetadataKey;

    /**
     * Constructor for the Vertex AI Ranker Scoring Model.
     *
     * @param projectId  The Google Cloud Project ID.
     * @param projectNum The Google Cloud Project Number.
     * @param location   The Google Cloud Region.
     * @param model      The model to use (by default <code>semantic-ranker-512@latest</code>)
     * @param titleMetadataKey The name of the key to use as a title.
     */
    public VertexAiRankerScoringModel(String projectId, String projectNum, String location, String model, String titleMetadataKey) {
        this.projectId = ensureNotBlank(projectId, "projectId");
        this.projectNum = ensureNotBlank(projectNum, "projectNum");
        this.location = ensureNotBlank(location, "location");
        this.model = model;
        this.titleMetadataKey = titleMetadataKey;
    }

    /**
     * Scores all provided {@link TextSegment}s against a given query.
     *
     * @param segments The list of {@link TextSegment}s to score.
     * @param query    The query against which to score the segments.
     * @return the list of scores. The order of scores corresponds to the order of {@link TextSegment}s.
     */
    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        AtomicInteger counter = new AtomicInteger();

        try (RankServiceClient rankServiceClient = RankServiceClient.create(
            RankServiceSettings.newBuilder().build())) {

            RankRequest.Builder rankingRequestBuilder = RankRequest.newBuilder();

            if (model != null && !model.isEmpty()) {
                rankingRequestBuilder.setModel(model);
            }

            rankingRequestBuilder
                .setRankingConfig(RankingConfigName.newBuilder()
                    .setProject(projectId)
                    .setLocation(location)
                    .setRankingConfig(
                        String.format("projects/%s/locations/%s/rankingConfigs/default_ranking_config.", projectNum, location))
                    .build().getRankingConfig())
                .setQuery(query)
                .setIgnoreRecordDetailsInResponse(true)
                .addAllRecords(segments.stream()
                    .map(segment -> {
                        RankingRecord.Builder rankingBuilder = RankingRecord.newBuilder()
                            .setContent(segment.text());
                        // Ranker API takes into account titles in its score calculations
                        String titleKeyToUse = titleMetadataKey != null ? titleMetadataKey : "title";
                        if (segment.metadata().getString(titleKeyToUse) != null) {
                            rankingBuilder.setTitle(segment.metadata().getString(titleKeyToUse));
                        }
                        // custom ID used to reorder the (sorted) results back into original segment order
                        rankingBuilder.setId(String.valueOf(counter.getAndIncrement()));
                        return rankingBuilder.build();
                    })
                    .collect(Collectors.toList()));

            RankResponse rankResponse = rankServiceClient.rank(rankingRequestBuilder.build());

            return Response.from(rankResponse.getRecordsList().stream()
                .peek(rankingRecord -> {
                    System.out.println(rankingRecord.toString());
                })
                // the API returns results sorted by relevance score, so reorder them back to original order
                .sorted((rr1, rr2) -> (Double.valueOf(rr1.getId()) > Double.valueOf(rr2.getId())) ? 1 : -1)
                .map(RankingRecord::getScore)
                .map(Double::valueOf)
                .collect(Collectors.toList()));
        } catch (IOException e) {
            throw new RuntimeException("An error happened when instantiating the RankServiceSettings class.", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private String projectId;
        private String projectNum;
        private String location;
        private String titleMetadataKey;

        public Builder model(String model) {
            this.model = ensureNotBlank(model, "model");
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder projectNum(String projectNum) {
            this.projectNum = projectNum;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = ensureNotBlank(titleMetadataKey, "titleMetadataKey");
            return this;
        }

        public VertexAiRankerScoringModel build() {
            return new VertexAiRankerScoringModel(projectId, projectNum, location, model, titleMetadataKey);
        }
    }
}
