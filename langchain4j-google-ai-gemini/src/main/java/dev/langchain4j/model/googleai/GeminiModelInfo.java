package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiModelInfo(
        @JsonProperty("name") String name,
        @JsonProperty("baseModelId") String baseModelId,
        @JsonProperty("version") String version,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("inputTokenLimit") Integer inputTokenLimit,
        @JsonProperty("outputTokenLimit") Integer outputTokenLimit,
        @JsonProperty("supportedGenerationMethods") List<String> supportedGenerationMethods,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("maxTemperature") Double maxTemperature,
        @JsonProperty("topP") Double topP,
        @JsonProperty("topK") Integer topK) {}
