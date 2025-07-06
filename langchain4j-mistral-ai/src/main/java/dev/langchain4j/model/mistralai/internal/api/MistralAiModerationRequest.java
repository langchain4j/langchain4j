package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class MistralAiModerationRequest {

    private final String model;
    private final List<String> input;

    public MistralAiModerationRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input;
    }

    public String getModel() {
        return model;
    }

    public List<String> getInput() {
        return input;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String model;
        private List<String> input;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(List<String> input) {
            this.input = input;
            return this;
        }

        public MistralAiModerationRequest build() {
            return new MistralAiModerationRequest(this);
        }
    }
}
