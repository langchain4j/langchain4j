package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
public class DocumentSearchRequest {
    @SerializedName(value = "db_name")
    private String dbName;

    @SerializedName(value = "space_name")
    private String spaceName;

    @SerializedName(value = "vector_value")
    private Boolean vectorValue;

    private Integer size;

    private Query query;

    @Getter
    @Setter
    public static class Query {
        @SerializedName(value = "document_ids")
        private List<String> documentIds;
        private List<Vector> vector;
    }

    @Getter
    @Setter
    public static class Vector{
        private String field;
        private List<Float> feature;
        @SerializedName(value = "min_score")
        private Double minScore;
    }
}
