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
public class DocumentUpsertRequest {
    @SerializedName(value = "db_name")
    private String dbName;

    @SerializedName(value = "space_name")
    private String spaceName;

    private List<Map<String, Object>> documents;
}
