package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.internal.Utils;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiCountTokensRequest(
        @JsonProperty("contents") @Nullable List<GeminiContent> contents,
        @JsonProperty("generateContentRequest") @Nullable GeminiGenerateContentRequest generateContentRequest) {
    GeminiCountTokensRequest {
        if (Utils.isNullOrEmpty(contents) && generateContentRequest == null) {
            throw new IllegalArgumentException("Either contents or generateContentRequest should be set");
        }
    }
}
