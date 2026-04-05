package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroundingMetadata(
        @JsonProperty("groundingChunks") List<GroundingChunk> groundingChunks,
        @JsonProperty("groundingSupports") List<GroundingSupport> groundingSupports,
        @JsonProperty("webSearchQueries") List<String> webSearchQueries,
        @JsonProperty("searchEntryPoint") SearchEntryPoint searchEntryPoint,
        @JsonProperty("retrievalMetadata") RetrievalMetadata retrievalMetadata,
        @JsonProperty("googleMapsWidgetContextToken") String googleMapsWidgetContextToken) {

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
    public record GroundingChunk(
            @JsonProperty("web") Web web,
            @JsonProperty("retrievedContext") RetrievedContext retrievedContext,
            @JsonProperty("maps") Maps maps) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Web(
                @JsonProperty("uri") String uri,
                @JsonProperty("title") String title) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RetrievedContext(
                @JsonProperty("uri") String uri,
                @JsonProperty("title") String title,
                @JsonProperty("text") String text) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Maps(
                @JsonProperty("uri") String uri,
                @JsonProperty("title") String title,
                @JsonProperty("text") String text,
                @JsonProperty("placeId") String placeId,
                @JsonProperty("placeAnswerSources") PlaceAnswerSources placeAnswerSources) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            public record PlaceAnswerSources(
                    @JsonProperty("reviewSnippets") List<ReviewSnippet> reviewSnippets) {}

            @JsonIgnoreProperties(ignoreUnknown = true)
            public record ReviewSnippet(
                    @JsonProperty("reviewId") String reviewId,
                    @JsonProperty("googleMapsUri") String googleMapsUri,
                    @JsonProperty("title") String title) {}
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroundingSupport(
            @JsonProperty("groundingChunkIndices") List<Integer> groundingChunkIndices,
            @JsonProperty("confidenceScores") List<Double> confidenceScores,
            @JsonProperty("segment") Segment segment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(
            @JsonProperty("partIndex") Integer partIndex,
            @JsonProperty("startIndex") Integer startIndex,
            @JsonProperty("endIndex") Integer endIndex,
            @JsonProperty("text") String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchEntryPoint(
            @JsonProperty("renderedContent") String renderedContent,
            @JsonProperty("sdkBlob") String sdkBlob) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RetrievalMetadata(
            @JsonProperty("googleSearchDynamicRetrievalScore")
            Double googleSearchDynamicRetrievalScore) {}
}
