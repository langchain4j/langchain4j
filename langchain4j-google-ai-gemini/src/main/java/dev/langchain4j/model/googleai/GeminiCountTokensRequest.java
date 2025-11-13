package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.internal.Utils;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiCountTokensRequest(
        @Nullable List<GeminiContent> contents,
        @Nullable GeminiGenerateContentRequest generateContentRequest) {
    GeminiCountTokensRequest {
        if (Utils.isNullOrEmpty(contents) && generateContentRequest == null) {
            throw new IllegalArgumentException("Either contents or generateContentRequest should be set");
        }
    }
}
