package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiFunctionCallingConfig {
    private GeminiMode mode;
    private List<String> allowedFunctionNames;

    @JsonCreator
    public GeminiFunctionCallingConfig(@JsonProperty("mode") GeminiMode mode, @JsonProperty("allowedFunctionNames") List<String> allowedFunctionNames) {
        this.mode = mode;
        this.allowedFunctionNames = allowedFunctionNames;
    }

    public static GeminiFunctionCallingConfigBuilder builder() {
        return new GeminiFunctionCallingConfigBuilder();
    }

    public GeminiMode getMode() {
        return this.mode;
    }

    public List<String> getAllowedFunctionNames() {
        return this.allowedFunctionNames;
    }

    public void setMode(GeminiMode mode) {
        this.mode = mode;
    }

    public void setAllowedFunctionNames(List<String> allowedFunctionNames) {
        this.allowedFunctionNames = allowedFunctionNames;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiFunctionCallingConfig)) return false;
        final GeminiFunctionCallingConfig other = (GeminiFunctionCallingConfig) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$mode = this.getMode();
        final Object other$mode = other.getMode();
        if (this$mode == null ? other$mode != null : !this$mode.equals(other$mode)) return false;
        final Object this$allowedFunctionNames = this.getAllowedFunctionNames();
        final Object other$allowedFunctionNames = other.getAllowedFunctionNames();
        if (this$allowedFunctionNames == null ? other$allowedFunctionNames != null : !this$allowedFunctionNames.equals(other$allowedFunctionNames))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiFunctionCallingConfig;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $mode = this.getMode();
        result = result * PRIME + ($mode == null ? 43 : $mode.hashCode());
        final Object $allowedFunctionNames = this.getAllowedFunctionNames();
        result = result * PRIME + ($allowedFunctionNames == null ? 43 : $allowedFunctionNames.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiFunctionCallingConfig(mode=" + this.getMode() + ", allowedFunctionNames=" + this.getAllowedFunctionNames() + ")";
    }

    public static class GeminiFunctionCallingConfigBuilder {
        private GeminiMode mode;
        private List<String> allowedFunctionNames;

        GeminiFunctionCallingConfigBuilder() {
        }

        public GeminiFunctionCallingConfigBuilder mode(GeminiMode mode) {
            this.mode = mode;
            return this;
        }

        public GeminiFunctionCallingConfigBuilder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        public GeminiFunctionCallingConfig build() {
            return new GeminiFunctionCallingConfig(this.mode, this.allowedFunctionNames);
        }

        public String toString() {
            return "GeminiFunctionCallingConfig.GeminiFunctionCallingConfigBuilder(mode=" + this.mode + ", allowedFunctionNames=" + this.allowedFunctionNames + ")";
        }
    }
}
