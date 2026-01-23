package dev.langchain4j.model.googleai;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiCachedContent(
        List<GeminiContent> content,
        List<GeminiTool> tools,
        String createTime,
        String updateTime,
        GeminiUsageMetadata usageMetadata,
        String expireTime,
        String ttl,
        String name,
        String displayName,
        String model,
        GeminiContent systemInstruction,
        GeminiToolConfig toolConfig
) {

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private List<GeminiContent> content;
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

        public Builder content(List<GeminiContent> content) {
            this.content = content;
            return this;
        }

        public Builder tools(List<GeminiTool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder createTime(String createTime) {
            this.createTime = createTime;
            return this;
        }

        public Builder updateTime(String updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public Builder usageMetadata(GeminiUsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
            return this;
        }

        public Builder expireTime(String expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        public Builder ttl(String ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemInstruction(GeminiContent systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public Builder toolConfig(GeminiToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }

        public GeminiCachedContent build() {
            return new GeminiCachedContent(
                    content,
                    tools,
                    createTime,
                    updateTime,
                    usageMetadata,
                    expireTime,
                    ttl,
                    name,
                    displayName,
                    model,
                    systemInstruction,
                    toolConfig
            );
        }

    }

    public GeminiCachedContent withModel(String model) {
        return new GeminiCachedContent(
                this.content,
                this.tools,
                this.createTime,
                this.updateTime,
                this.usageMetadata,
                this.expireTime,
                this.ttl,
                this.name,
                this.displayName,
                model,
                this.systemInstruction,
                this.toolConfig
        );
    }

}
