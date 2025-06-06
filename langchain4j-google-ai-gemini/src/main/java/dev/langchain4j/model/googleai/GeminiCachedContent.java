package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonCreator
    GeminiCachedContent(
            @JsonProperty("contents") List<GeminiContent> contents,
            @JsonProperty("tools") List<GeminiTool> tools,
            @JsonProperty("createTime") String createTime,
            @JsonProperty("updateTime") String updateTime,
            @JsonProperty("usageMetadata") GeminiUsageMetadata usageMetadata,
            @JsonProperty("expireTime") String expireTime,
            @JsonProperty("ttl") String ttl,
            @JsonProperty("name") String name,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("model") String model,
            @JsonProperty("systemInstruction") GeminiContent systemInstruction,
            @JsonProperty("toolConfig") GeminiToolConfig toolConfig) {
        this.contents = contents;
        this.tools = tools;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.usageMetadata = usageMetadata;
        this.expireTime = expireTime;
        this.ttl = ttl;
        this.name = name;
        this.displayName = displayName;
        this.model = model;
        this.systemInstruction = systemInstruction;
        this.toolConfig = toolConfig;
    }

    public static GeminiCachedContentBuilder builder() {
        return new GeminiCachedContentBuilder();
    }

    public List<GeminiContent> getContents() {
        return this.contents;
    }

    public List<GeminiTool> getTools() {
        return this.tools;
    }

    public String getCreateTime() {
        return this.createTime;
    }

    public String getUpdateTime() {
        return this.updateTime;
    }

    public GeminiUsageMetadata getUsageMetadata() {
        return this.usageMetadata;
    }

    public String getExpireTime() {
        return this.expireTime;
    }

    public String getTtl() {
        return this.ttl;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getModel() {
        return this.model;
    }

    public GeminiContent getSystemInstruction() {
        return this.systemInstruction;
    }

    public GeminiToolConfig getToolConfig() {
        return this.toolConfig;
    }

    public void setContents(List<GeminiContent> contents) {
        this.contents = contents;
    }

    public void setTools(List<GeminiTool> tools) {
        this.tools = tools;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public void setUsageMetadata(GeminiUsageMetadata usageMetadata) {
        this.usageMetadata = usageMetadata;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setSystemInstruction(GeminiContent systemInstruction) {
        this.systemInstruction = systemInstruction;
    }

    public void setToolConfig(GeminiToolConfig toolConfig) {
        this.toolConfig = toolConfig;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCachedContent)) return false;
        final GeminiCachedContent other = (GeminiCachedContent) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$contents = this.getContents();
        final Object other$contents = other.getContents();
        if (this$contents == null ? other$contents != null : !this$contents.equals(other$contents)) return false;
        final Object this$tools = this.getTools();
        final Object other$tools = other.getTools();
        if (this$tools == null ? other$tools != null : !this$tools.equals(other$tools)) return false;
        final Object this$createTime = this.getCreateTime();
        final Object other$createTime = other.getCreateTime();
        if (this$createTime == null ? other$createTime != null : !this$createTime.equals(other$createTime))
            return false;
        final Object this$updateTime = this.getUpdateTime();
        final Object other$updateTime = other.getUpdateTime();
        if (this$updateTime == null ? other$updateTime != null : !this$updateTime.equals(other$updateTime))
            return false;
        final Object this$usageMetadata = this.getUsageMetadata();
        final Object other$usageMetadata = other.getUsageMetadata();
        if (this$usageMetadata == null ? other$usageMetadata != null : !this$usageMetadata.equals(other$usageMetadata))
            return false;
        final Object this$expireTime = this.getExpireTime();
        final Object other$expireTime = other.getExpireTime();
        if (this$expireTime == null ? other$expireTime != null : !this$expireTime.equals(other$expireTime))
            return false;
        final Object this$ttl = this.getTtl();
        final Object other$ttl = other.getTtl();
        if (this$ttl == null ? other$ttl != null : !this$ttl.equals(other$ttl)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$displayName = this.getDisplayName();
        final Object other$displayName = other.getDisplayName();
        if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName))
            return false;
        final Object this$model = this.getModel();
        final Object other$model = other.getModel();
        if (this$model == null ? other$model != null : !this$model.equals(other$model)) return false;
        final Object this$systemInstruction = this.getSystemInstruction();
        final Object other$systemInstruction = other.getSystemInstruction();
        if (this$systemInstruction == null ? other$systemInstruction != null : !this$systemInstruction.equals(other$systemInstruction))
            return false;
        final Object this$toolConfig = this.getToolConfig();
        final Object other$toolConfig = other.getToolConfig();
        if (this$toolConfig == null ? other$toolConfig != null : !this$toolConfig.equals(other$toolConfig))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCachedContent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $contents = this.getContents();
        result = result * PRIME + ($contents == null ? 43 : $contents.hashCode());
        final Object $tools = this.getTools();
        result = result * PRIME + ($tools == null ? 43 : $tools.hashCode());
        final Object $createTime = this.getCreateTime();
        result = result * PRIME + ($createTime == null ? 43 : $createTime.hashCode());
        final Object $updateTime = this.getUpdateTime();
        result = result * PRIME + ($updateTime == null ? 43 : $updateTime.hashCode());
        final Object $usageMetadata = this.getUsageMetadata();
        result = result * PRIME + ($usageMetadata == null ? 43 : $usageMetadata.hashCode());
        final Object $expireTime = this.getExpireTime();
        result = result * PRIME + ($expireTime == null ? 43 : $expireTime.hashCode());
        final Object $ttl = this.getTtl();
        result = result * PRIME + ($ttl == null ? 43 : $ttl.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $displayName = this.getDisplayName();
        result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
        final Object $model = this.getModel();
        result = result * PRIME + ($model == null ? 43 : $model.hashCode());
        final Object $systemInstruction = this.getSystemInstruction();
        result = result * PRIME + ($systemInstruction == null ? 43 : $systemInstruction.hashCode());
        final Object $toolConfig = this.getToolConfig();
        result = result * PRIME + ($toolConfig == null ? 43 : $toolConfig.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCachedContent(contents=" + this.getContents() + ", tools=" + this.getTools() + ", createTime=" + this.getCreateTime() + ", updateTime=" + this.getUpdateTime() + ", usageMetadata=" + this.getUsageMetadata() + ", expireTime=" + this.getExpireTime() + ", ttl=" + this.getTtl() + ", name=" + this.getName() + ", displayName=" + this.getDisplayName() + ", model=" + this.getModel() + ", systemInstruction=" + this.getSystemInstruction() + ", toolConfig=" + this.getToolConfig() + ")";
    }

    public static class GeminiCachedContentBuilder {
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

        GeminiCachedContentBuilder() {
        }

        public GeminiCachedContentBuilder contents(List<GeminiContent> contents) {
            this.contents = contents;
            return this;
        }

        public GeminiCachedContentBuilder tools(List<GeminiTool> tools) {
            this.tools = tools;
            return this;
        }

        public GeminiCachedContentBuilder createTime(String createTime) {
            this.createTime = createTime;
            return this;
        }

        public GeminiCachedContentBuilder updateTime(String updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public GeminiCachedContentBuilder usageMetadata(GeminiUsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
            return this;
        }

        public GeminiCachedContentBuilder expireTime(String expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        public GeminiCachedContentBuilder ttl(String ttl) {
            this.ttl = ttl;
            return this;
        }

        public GeminiCachedContentBuilder name(String name) {
            this.name = name;
            return this;
        }

        public GeminiCachedContentBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public GeminiCachedContentBuilder model(String model) {
            this.model = model;
            return this;
        }

        public GeminiCachedContentBuilder systemInstruction(GeminiContent systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public GeminiCachedContentBuilder toolConfig(GeminiToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }

        public GeminiCachedContent build() {
            return new GeminiCachedContent(this.contents, this.tools, this.createTime, this.updateTime, this.usageMetadata, this.expireTime, this.ttl, this.name, this.displayName, this.model, this.systemInstruction, this.toolConfig);
        }

        public String toString() {
            return "GeminiCachedContent.GeminiCachedContentBuilder(contents=" + this.contents + ", tools=" + this.tools + ", createTime=" + this.createTime + ", updateTime=" + this.updateTime + ", usageMetadata=" + this.usageMetadata + ", expireTime=" + this.expireTime + ", ttl=" + this.ttl + ", name=" + this.name + ", displayName=" + this.displayName + ", model=" + this.model + ", systemInstruction=" + this.systemInstruction + ", toolConfig=" + this.toolConfig + ")";
        }
    }
}
