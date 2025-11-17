package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiGenerateContentRequest(
        @JsonProperty List<GeminiContent> contents,
        @JsonProperty GeminiTool tools,
        @JsonProperty GeminiToolConfig toolConfig,
        @JsonProperty List<GeminiSafetySetting> safetySettings,
        @JsonProperty GeminiContent systemInstruction,
        @JsonProperty GeminiGenerationConfig generationConfig,
        @JsonProperty String cachedContent) {

    static GeminiGenerateContentRequestBuilder builder() {
        return new GeminiGenerateContentRequestBuilder();
    }

    static class GeminiGenerateContentRequestBuilder {
        private List<GeminiContent> contents;
        private GeminiTool tools;
        private GeminiToolConfig toolConfig;
        private List<GeminiSafetySetting> safetySettings;
        private GeminiContent systemInstruction;
        private GeminiGenerationConfig generationConfig;
        private String cachedContent;

        GeminiGenerateContentRequestBuilder() {}

        GeminiGenerateContentRequestBuilder contents(List<GeminiContent> contents) {
            this.contents = contents;
            return this;
        }

        GeminiGenerateContentRequestBuilder tools(GeminiTool tools) {
            this.tools = tools;
            return this;
        }

        GeminiGenerateContentRequestBuilder toolConfig(GeminiToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }

        GeminiGenerateContentRequestBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        GeminiGenerateContentRequestBuilder systemInstruction(GeminiContent systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        GeminiGenerateContentRequestBuilder generationConfig(GeminiGenerationConfig generationConfig) {
            this.generationConfig = generationConfig;
            return this;
        }

        GeminiGenerateContentRequestBuilder cachedContent(String cachedContent) {
            this.cachedContent = cachedContent;
            return this;
        }

        public GeminiGenerateContentRequest build() {
            return new GeminiGenerateContentRequest(
                    this.contents,
                    this.tools,
                    this.toolConfig,
                    this.safetySettings,
                    this.systemInstruction,
                    this.generationConfig,
                    this.cachedContent);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiTool(List<GeminiFunctionDeclaration> functionDeclarations, GeminiCodeExecution codeExecution) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiCodeExecution() {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiToolConfig(GeminiFunctionCallingConfig functionCallingConfig) {}
}
