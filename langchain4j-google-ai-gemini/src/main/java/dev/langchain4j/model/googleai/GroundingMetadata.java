package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroundingMetadata(
        List<GroundingChunk> groundingChunks,
        List<GroundingSupport> groundingSupports,
        List<String> webSearchQueries,
        SearchEntryPoint searchEntryPoint,
        RetrievalMetadata retrievalMetadata,
        String googleMapsWidgetContextToken) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<GroundingChunk> groundingChunks;
        private List<GroundingSupport> groundingSupports;
        private List<String> webSearchQueries;
        private SearchEntryPoint searchEntryPoint;
        private RetrievalMetadata retrievalMetadata;
        private String googleMapsWidgetContextToken;

        public Builder groundingChunks(List<GroundingChunk> groundingChunks) {
            this.groundingChunks = groundingChunks;
            return this;
        }

        public Builder groundingSupports(List<GroundingSupport> groundingSupports) {
            this.groundingSupports = groundingSupports;
            return this;
        }

        public Builder webSearchQueries(List<String> webSearchQueries) {
            this.webSearchQueries = webSearchQueries;
            return this;
        }

        public Builder searchEntryPoint(SearchEntryPoint searchEntryPoint) {
            this.searchEntryPoint = searchEntryPoint;
            return this;
        }

        public Builder retrievalMetadata(RetrievalMetadata retrievalMetadata) {
            this.retrievalMetadata = retrievalMetadata;
            return this;
        }

        public Builder googleMapsWidgetContextToken(String googleMapsWidgetContextToken) {
            this.googleMapsWidgetContextToken = googleMapsWidgetContextToken;
            return this;
        }

        public GroundingMetadata build() {
            return new GroundingMetadata(
                    groundingChunks,
                    groundingSupports,
                    webSearchQueries,
                    searchEntryPoint,
                    retrievalMetadata,
                    googleMapsWidgetContextToken);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroundingChunk(Web web, RetrievedContext retrievedContext, Maps maps) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Web(String uri, String title) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RetrievedContext(String uri, String title, String text) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Maps(
                String uri, String title, String text, String placeId, PlaceAnswerSources placeAnswerSources) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            public record PlaceAnswerSources(List<ReviewSnippet> reviewSnippets) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            public record ReviewSnippet(String reviewId, String googleMapsUri, String title) {}
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroundingSupport(
            List<Integer> groundingChunkIndices, List<Double> confidenceScores, Segment segment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(Integer partIndex, Integer startIndex, Integer endIndex, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchEntryPoint(String renderedContent, String sdkBlob) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrievalMetadata(Double googleSearchDynamicRetrievalScore) {}
}
