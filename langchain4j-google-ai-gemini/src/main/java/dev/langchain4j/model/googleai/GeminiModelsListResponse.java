package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiModelsListResponse(
        @JsonProperty("models") List<GeminiModelInfo> models,
        @JsonProperty("nextPageToken") String nextPageToken) {}
