package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DocumentSearchResponse {
    private Integer code;
    private String msg;
    private List<List<Document>> documents;

    @Getter
    @Setter
    public static class Document{
        @SerializedName(value = "_id")
        private String id;

        @SerializedName(value = "_score")
        private Double score;

        @SerializedName(value = "_source")
        private Map<String, Object> source;
    }
}
