package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
class BulkResponse {

    private Integer status;
    private String error;
    @SerializedName("_id")
    private String id;
}
