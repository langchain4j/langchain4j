package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiFimCompletionRequest {

    private String model;
    private String prompt;
    private String suffix;
    private Double temperature;
    private Integer maxTokens;
    private Integer minTokens;
    private Double topP;
    private Boolean stream;
    private Integer randomSeed;
    private List<String> stop;

    private MistralAiFimCompletionRequest(MistralAiFimCompletionRequestBuilder builder) {
        this.model = builder.model;
        this.prompt = builder.prompt;
        this.suffix = builder.suffix;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.minTokens = builder.minTokens;
        this.topP = builder.topP;
        this.stream = builder.stream;
        this.randomSeed = builder.randomSeed;
        this.stop = builder.stop;
    }

    public String getModel() {
        return this.model;
    }

    public String getPrompt() {
        return this.prompt;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public Double getTemperature() {
        return this.temperature;
    }

    public Integer getMaxTokens() {
        return this.maxTokens;
    }

    public Integer getMinTokens() {
        return this.minTokens;
    }

    public Double getTopP() {
        return this.topP;
    }

    public Boolean getStream() {
        return this.stream;
    }

    public Integer getRandomSeed() {
        return this.randomSeed;
    }

    public List<String> getStop() {
        return this.stop;
    }

    public static MistralAiFimCompletionRequestBuilder builder() {
        return new MistralAiFimCompletionRequestBuilder();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.model);
        hash = 89 * hash + Objects.hashCode(this.prompt);
        hash = 89 * hash + Objects.hashCode(this.suffix);
        hash = 89 * hash + Objects.hashCode(this.temperature);
        hash = 89 * hash + Objects.hashCode(this.maxTokens);
        hash = 89 * hash + Objects.hashCode(this.minTokens);
        hash = 89 * hash + Objects.hashCode(this.topP);
        hash = 89 * hash + Objects.hashCode(this.stream);
        hash = 89 * hash + Objects.hashCode(this.randomSeed);
        hash = 89 * hash + Objects.hashCode(this.stop);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiFimCompletionRequest other = (MistralAiFimCompletionRequest) obj;
        return Objects.equals(this.model, other.model)
                && Objects.equals(this.prompt, other.prompt)
                && Objects.equals(this.suffix, other.suffix)
                && Objects.equals(this.temperature, other.temperature)
                && Objects.equals(this.maxTokens, other.maxTokens)
                && Objects.equals(this.minTokens, other.minTokens)
                && Objects.equals(this.topP, other.topP)
                && Objects.equals(this.stream, other.stream)
                && Objects.equals(this.randomSeed, other.randomSeed)
                && Objects.equals(this.stop, other.stop);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MistralAiFimCompletionRequest.class.getSimpleName() + "[", "]")
                .add("model='" + model + "'")
                .add("prompt='" + prompt + "'")
                .add("suffix='" + suffix + "'")
                .add("temperature=" + temperature)
                .add("maxTokens=" + maxTokens)
                .add("minTokens=" + minTokens)
                .add("topP=" + topP)
                .add("stream=" + stream)
                .add("randomSeed=" + randomSeed)
                .add("stop=" + stop)
                .toString();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiFimCompletionRequestBuilder {
        private String model;
        private String prompt;
        private String suffix;
        private Double temperature;
        private Integer maxTokens;
        private Integer minTokens;
        private Double topP;
        private Boolean stream;
        private Integer randomSeed;
        private List<String> stop;

        private MistralAiFimCompletionRequestBuilder() {}

        public MistralAiFimCompletionRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder suffix(String suffix) {
            this.suffix = suffix;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder minTokens(Integer minTokens) {
            this.minTokens = minTokens;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public MistralAiFimCompletionRequestBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public MistralAiFimCompletionRequest build() {
            return new MistralAiFimCompletionRequest(this);
        }
    }
}
