package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiGenerateContentRequest(
        String model,
        List<GeminiContent> contents,
        GeminiTool tools,
        GeminiToolConfig toolConfig,
        List<GeminiSafetySetting> safetySettings,
        GeminiContent systemInstruction,
        GeminiGenerationConfig generationConfig) {

    static GeminiGenerateContentRequestBuilder builder() {
        return new GeminiGenerateContentRequestBuilder();
    }

    static class GeminiGenerateContentRequestBuilder {
        private String model;
        private List<GeminiContent> contents;
        private GeminiTool tools;
        private GeminiToolConfig toolConfig;
        private List<GeminiSafetySetting> safetySettings;
        private GeminiContent systemInstruction;
        private GeminiGenerationConfig generationConfig;

        GeminiGenerateContentRequestBuilder() {}

        GeminiGenerateContentRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

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

        public GeminiGenerateContentRequest build() {
            return new GeminiGenerateContentRequest(
                    this.model,
                    this.contents,
                    this.tools,
                    this.toolConfig,
                    this.safetySettings,
                    this.systemInstruction,
                    this.generationConfig);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiTool(
            List<GeminiFunctionDeclaration> functionDeclarations,
            GeminiCodeExecution codeExecution,
            @JsonProperty("google_search") GeminiGoogleSearchRetrieval googleSearch,
            GeminiUrlContext urlContext,
            GeminiGoogleMaps googleMaps) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiCodeExecution() {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiUrlContext() {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiGoogleSearchRetrieval() {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminiGoogleMaps(Boolean enableWidget) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiToolConfig(GeminiFunctionCallingConfig functionCallingConfig) {}
}
