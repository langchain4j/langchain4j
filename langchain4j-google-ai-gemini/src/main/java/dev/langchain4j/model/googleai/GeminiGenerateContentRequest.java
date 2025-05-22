package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiGenerateContentRequest {
    private String model;
    private List<GeminiContent> contents;
    private GeminiTool tools;
    private GeminiToolConfig toolConfig;
    private List<GeminiSafetySetting> safetySettings;
    private GeminiContent systemInstruction;
    private GeminiGenerationConfig generationConfig;
    private String cachedContent;

    @JsonCreator
    GeminiGenerateContentRequest(
            @JsonProperty("model") String model,
            @JsonProperty("contents") List<GeminiContent> contents,
            @JsonProperty("tools") GeminiTool tools,
            @JsonProperty("toolConfig") GeminiToolConfig toolConfig,
            @JsonProperty("safetySettings") List<GeminiSafetySetting> safetySettings,
            @JsonProperty("systemInstruction") GeminiContent systemInstruction,
            @JsonProperty("generationConfig") GeminiGenerationConfig generationConfig,
            @JsonProperty("cachedContent") String cachedContent) {
        this.model = model;
        this.contents = contents;
        this.tools = tools;
        this.toolConfig = toolConfig;
        this.safetySettings = safetySettings;
        this.systemInstruction = systemInstruction;
        this.generationConfig = generationConfig;
        this.cachedContent = cachedContent;
    }

    public static GeminiGenerateContentRequestBuilder builder() {
        return new GeminiGenerateContentRequestBuilder();
    }

    public String getModel() {
        return this.model;
    }

    public List<GeminiContent> getContents() {
        return this.contents;
    }

    public GeminiTool getTools() {
        return this.tools;
    }

    public GeminiToolConfig getToolConfig() {
        return this.toolConfig;
    }

    public List<GeminiSafetySetting> getSafetySettings() {
        return this.safetySettings;
    }

    public GeminiContent getSystemInstruction() {
        return this.systemInstruction;
    }

    public GeminiGenerationConfig getGenerationConfig() {
        return this.generationConfig;
    }

    public String getCachedContent() {
        return this.cachedContent;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setContents(List<GeminiContent> contents) {
        this.contents = contents;
    }

    public void setTools(GeminiTool tools) {
        this.tools = tools;
    }

    public void setToolConfig(GeminiToolConfig toolConfig) {
        this.toolConfig = toolConfig;
    }

    public void setSafetySettings(List<GeminiSafetySetting> safetySettings) {
        this.safetySettings = safetySettings;
    }

    public void setSystemInstruction(GeminiContent systemInstruction) {
        this.systemInstruction = systemInstruction;
    }

    public void setGenerationConfig(GeminiGenerationConfig generationConfig) {
        this.generationConfig = generationConfig;
    }

    public void setCachedContent(String cachedContent) {
        this.cachedContent = cachedContent;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiGenerateContentRequest)) return false;
        final GeminiGenerateContentRequest other = (GeminiGenerateContentRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$model = this.getModel();
        final Object other$model = other.getModel();
        if (this$model == null ? other$model != null : !this$model.equals(other$model)) return false;
        final Object this$contents = this.getContents();
        final Object other$contents = other.getContents();
        if (this$contents == null ? other$contents != null : !this$contents.equals(other$contents)) return false;
        final Object this$tools = this.getTools();
        final Object other$tools = other.getTools();
        if (this$tools == null ? other$tools != null : !this$tools.equals(other$tools)) return false;
        final Object this$toolConfig = this.getToolConfig();
        final Object other$toolConfig = other.getToolConfig();
        if (this$toolConfig == null ? other$toolConfig != null : !this$toolConfig.equals(other$toolConfig))
            return false;
        final Object this$safetySettings = this.getSafetySettings();
        final Object other$safetySettings = other.getSafetySettings();
        if (this$safetySettings == null ? other$safetySettings != null : !this$safetySettings.equals(other$safetySettings))
            return false;
        final Object this$systemInstruction = this.getSystemInstruction();
        final Object other$systemInstruction = other.getSystemInstruction();
        if (this$systemInstruction == null ? other$systemInstruction != null : !this$systemInstruction.equals(other$systemInstruction))
            return false;
        final Object this$generationConfig = this.getGenerationConfig();
        final Object other$generationConfig = other.getGenerationConfig();
        if (this$generationConfig == null ? other$generationConfig != null : !this$generationConfig.equals(other$generationConfig))
            return false;
        final Object this$cachedContent = this.getCachedContent();
        final Object other$cachedContent = other.getCachedContent();
        if (this$cachedContent == null ? other$cachedContent != null : !this$cachedContent.equals(other$cachedContent))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiGenerateContentRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $model = this.getModel();
        result = result * PRIME + ($model == null ? 43 : $model.hashCode());
        final Object $contents = this.getContents();
        result = result * PRIME + ($contents == null ? 43 : $contents.hashCode());
        final Object $tools = this.getTools();
        result = result * PRIME + ($tools == null ? 43 : $tools.hashCode());
        final Object $toolConfig = this.getToolConfig();
        result = result * PRIME + ($toolConfig == null ? 43 : $toolConfig.hashCode());
        final Object $safetySettings = this.getSafetySettings();
        result = result * PRIME + ($safetySettings == null ? 43 : $safetySettings.hashCode());
        final Object $systemInstruction = this.getSystemInstruction();
        result = result * PRIME + ($systemInstruction == null ? 43 : $systemInstruction.hashCode());
        final Object $generationConfig = this.getGenerationConfig();
        result = result * PRIME + ($generationConfig == null ? 43 : $generationConfig.hashCode());
        final Object $cachedContent = this.getCachedContent();
        result = result * PRIME + ($cachedContent == null ? 43 : $cachedContent.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiGenerateContentRequest(model=" + this.getModel() + ", contents=" + this.getContents() + ", tools=" + this.getTools() + ", toolConfig=" + this.getToolConfig() + ", safetySettings=" + this.getSafetySettings() + ", systemInstruction=" + this.getSystemInstruction() + ", generationConfig=" + this.getGenerationConfig() + ", cachedContent=" + this.getCachedContent() + ")";
    }

    public static class GeminiGenerateContentRequestBuilder {
        private String model;
        private List<GeminiContent> contents;
        private GeminiTool tools;
        private GeminiToolConfig toolConfig;
        private List<GeminiSafetySetting> safetySettings;
        private GeminiContent systemInstruction;
        private GeminiGenerationConfig generationConfig;
        private String cachedContent;

        GeminiGenerateContentRequestBuilder() {
        }

        public GeminiGenerateContentRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public GeminiGenerateContentRequestBuilder contents(List<GeminiContent> contents) {
            this.contents = contents;
            return this;
        }

        public GeminiGenerateContentRequestBuilder tools(GeminiTool tools) {
            this.tools = tools;
            return this;
        }

        public GeminiGenerateContentRequestBuilder toolConfig(GeminiToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }

        public GeminiGenerateContentRequestBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public GeminiGenerateContentRequestBuilder systemInstruction(GeminiContent systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public GeminiGenerateContentRequestBuilder generationConfig(GeminiGenerationConfig generationConfig) {
            this.generationConfig = generationConfig;
            return this;
        }

        public GeminiGenerateContentRequestBuilder cachedContent(String cachedContent) {
            this.cachedContent = cachedContent;
            return this;
        }

        public GeminiGenerateContentRequest build() {
            return new GeminiGenerateContentRequest(this.model, this.contents, this.tools, this.toolConfig, this.safetySettings, this.systemInstruction, this.generationConfig, this.cachedContent);
        }

        public String toString() {
            return "GeminiGenerateContentRequest.GeminiGenerateContentRequestBuilder(model=" + this.model + ", contents=" + this.contents + ", tools=" + this.tools + ", toolConfig=" + this.toolConfig + ", safetySettings=" + this.safetySettings + ", systemInstruction=" + this.systemInstruction + ", generationConfig=" + this.generationConfig + ", cachedContent=" + this.cachedContent + ")";
        }
    }
}
