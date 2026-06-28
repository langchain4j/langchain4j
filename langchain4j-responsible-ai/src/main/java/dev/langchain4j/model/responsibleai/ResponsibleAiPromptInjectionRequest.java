package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
class ResponsibleAiPromptInjectionRequest {

    @JsonProperty("content")
    private final String text;

    @JsonProperty("context")
    private final String context;

    @JsonProperty("sensitivity")
    private final String sensitivity;

    private ResponsibleAiPromptInjectionRequest(Builder builder) {
        this.text = builder.text;
        this.context = builder.context;
        this.sensitivity = builder.sensitivity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private String context;
        private String sensitivity;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder sensitivity(String sensitivity) {
            this.sensitivity = sensitivity;
            return this;
        }

        public ResponsibleAiPromptInjectionRequest build() {
            return new ResponsibleAiPromptInjectionRequest(this);
        }
    }
}
