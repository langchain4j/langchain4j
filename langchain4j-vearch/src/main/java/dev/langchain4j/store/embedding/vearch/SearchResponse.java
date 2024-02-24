package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
class SearchResponse {

    private Integer took;
    @SerializedName("timed_out")
    private Boolean timeout;
    /**
     * not support shards yet
     */
    @SerializedName("_shards")
    private Object shards;
    private Hit hits;

    @Getter
    @Setter
    @Builder
    public static class Hit {

        private Integer total;
        private Double maxScore;
        private List<SearchedDocument> hits;
    }

    @Getter
    @Setter
    @Builder
    public static class SearchedDocument {

        @SerializedName("_id")
        private String id;
        @SerializedName("_score")
        private Double score;
        @SerializedName("_source")
        private Map<String, Object> source;
    }
}
