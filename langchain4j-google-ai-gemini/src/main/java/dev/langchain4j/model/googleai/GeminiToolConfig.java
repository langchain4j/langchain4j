package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiToolConfig {
    private GeminiFunctionCallingConfig functionCallingConfig;

    @JsonCreator
    public GeminiToolConfig(@JsonProperty("functionCallingConfig") GeminiFunctionCallingConfig functionCallingConfig) {
        this.functionCallingConfig = functionCallingConfig;
    }

    public GeminiFunctionCallingConfig getFunctionCallingConfig() {
        return this.functionCallingConfig;
    }

    public void setFunctionCallingConfig(GeminiFunctionCallingConfig functionCallingConfig) {
        this.functionCallingConfig = functionCallingConfig;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiToolConfig)) return false;
        final GeminiToolConfig other = (GeminiToolConfig) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$functionCallingConfig = this.getFunctionCallingConfig();
        final Object other$functionCallingConfig = other.getFunctionCallingConfig();
        if (this$functionCallingConfig == null ? other$functionCallingConfig != null : !this$functionCallingConfig.equals(other$functionCallingConfig))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiToolConfig;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $functionCallingConfig = this.getFunctionCallingConfig();
        result = result * PRIME + ($functionCallingConfig == null ? 43 : $functionCallingConfig.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiToolConfig(functionCallingConfig=" + this.getFunctionCallingConfig() + ")";
    }
}
