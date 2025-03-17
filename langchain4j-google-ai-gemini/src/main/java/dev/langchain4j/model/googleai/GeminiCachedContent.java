package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
class GeminiCachedContent {
    private List<GeminiContent> contents;
    private List<GeminiTool> tools;
    private String createTime;
    private String updateTime;
    private GeminiUsageMetadata usageMetadata;
    private String expireTime;
    private String ttl;
    private String name;
    private String displayName;
    private String model;
    private GeminiContent systemInstruction;
    private GeminiToolConfig toolConfig;
}
