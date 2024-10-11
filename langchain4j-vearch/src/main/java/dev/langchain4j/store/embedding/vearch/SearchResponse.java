package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class SearchResponse {

    private Integer took;
    @JsonProperty("timed_out")
    private Boolean timeout;
    /**
     * not support shards yet
     */
    @JsonProperty("_shards")
    private Object shards;
    private Hit hits;

    SearchResponse() {

    }

    SearchResponse(Integer took, Boolean timeout, Hit hits, Object shards) {
        this.took = took;
        this.timeout = timeout;
        this.hits = hits;
        this.shards = shards;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class Hit {

        private Integer total;
        private Double maxScore;
        private List<SearchedDocument> hits;

        Hit() {

        }

        Hit(Integer total, Double maxScore, List<SearchedDocument> hits) {
            this.total = total;
            this.maxScore = maxScore;
            this.hits = hits;
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    static class SearchedDocument {

        @JsonProperty("_id")
        private String id;
        @JsonProperty("_score")
        private Double score;
        @JsonProperty("_source")
        private Map<String, Object> source;

        SearchedDocument() {

        }

        SearchedDocument(String id, Double score, Map<String, Object> source) {
            this.id = id;
            this.score = score;
            this.source = source;
        }
    }
}
