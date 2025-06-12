package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GeminiFunctionCallingConfig {

    @JsonProperty
    private GeminiMode mode;
    @JsonProperty
    private List<String> allowedFunctionNames;

    public GeminiFunctionCallingConfig(GeminiFunctionCallingConfigBuilder builder) {
        this.mode = builder.mode;
        this.allowedFunctionNames = builder.allowedFunctionNames;
    }

    public GeminiFunctionCallingConfig(GeminiMode mode, List<String> allowedFunctionNames) {
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
            return new GeminiFunctionCallingConfig(this);
        }
    }
}
