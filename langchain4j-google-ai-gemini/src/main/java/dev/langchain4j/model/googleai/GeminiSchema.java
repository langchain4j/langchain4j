package dev.langchain4j.model.googleai;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
class GeminiSchema {
    private GeminiType type;
    private String format;
    private String description;
    private Boolean nullable;
    @SerializedName("enum")
    private List<String> enumeration;
    private String maxItems;
    private Map<String, GeminiSchema> properties;
    private List<String> required;
    private GeminiSchema items;
}
