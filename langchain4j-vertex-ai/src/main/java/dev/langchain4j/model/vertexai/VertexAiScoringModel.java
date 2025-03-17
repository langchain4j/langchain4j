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
import static java.util.Comparator.comparing;

/**
 * Implementation of a <code>ScoringModel</code> for the Google Cloud Vertex AI
 * <a href="https://cloud.google.com/generative-ai-app-builder/docs/ranking">Ranking API</a>.
 */
public class VertexAiScoringModel implements ScoringModel {

    private final String model;
    private final String projectId;
    private final String projectNumber;
    private final String location;
    private final String titleMetadataKey;

    /**
     * Constructor for the Vertex AI Ranker Scoring Model.
     *
     * @param projectId  The Google Cloud Project ID.
     * @param projectNumber The Google Cloud Project Number.
     * @param location   The Google Cloud Region.
     * @param model      The model to use
     * @param titleMetadataKey The name of the key to use as a title.
     */
    public VertexAiScoringModel(String projectId, String projectNumber, String location, String model, String titleMetadataKey) {
        this.projectId = ensureNotBlank(projectId, "projectId");
        this.projectNumber = ensureNotBlank(projectNumber, "projectNumber");
        this.location = ensureNotBlank(location, "location");
        this.model = ensureNotBlank(model, "model");
        this.titleMetadataKey = titleMetadataKey != null ? titleMetadataKey : "title";
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
                        String.format("projects/%s/locations/%s/rankingConfigs/default_ranking_config.", projectNumber, location))
                    .build().getRankingConfig())
                .setQuery(query)
                .setIgnoreRecordDetailsInResponse(true)
                .addAllRecords(segments.stream()
                    .map(segment -> {
                        RankingRecord.Builder rankingBuilder = RankingRecord.newBuilder()
                            .setContent(segment.text());
                        // Ranker API takes into account titles in its score calculations
                        if (segment.metadata().getString(titleMetadataKey) != null) {
                            rankingBuilder.setTitle(segment.metadata().getString(titleMetadataKey));
                        }
                        // custom ID used to reorder the (sorted) results back into original segment order
                        rankingBuilder.setId(String.valueOf(counter.getAndIncrement()));
                        return rankingBuilder.build();
                    })
                    .collect(Collectors.toList()));

            RankResponse rankResponse = rankServiceClient.rank(rankingRequestBuilder.build());

            return Response.from(rankResponse.getRecordsList().stream()
                // the API returns results sorted by relevance score, so reorder them back to original order
                .sorted(comparing(rr -> Double.valueOf(rr.getId())))
                .map(RankingRecord::getScore)
                .map(Double::valueOf)
                .collect(Collectors.toList()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private String projectId;
        private String projectNumber;
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

        public Builder projectNumber(String projectNumber) {
            this.projectNumber = projectNumber;
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

        public VertexAiScoringModel build() {
            return new VertexAiScoringModel(projectId, projectNumber, location, model, titleMetadataKey);
        }
    }
}
