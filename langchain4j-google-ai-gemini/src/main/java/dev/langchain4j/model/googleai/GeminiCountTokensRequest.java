package dev.langchain4j.model.googleai;

import lombok.Data;

import java.util.List;

@Data
class GeminiCountTokensRequest {
    List<GeminiContent> contents;
    GeminiGenerateContentRequest generateContentRequest;
}
