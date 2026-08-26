package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = MistralAiParameters.MistralAiParametersBuilder.class)
public class MistralAiParameters {
    private String type;
    private Map<String, Map<String, Object>> properties;
    private List<String> required;

    @JsonCreator
    private MistralAiParameters(MistralAiParametersBuilder builder) {
        this.type = builder.type;
        this.properties = builder.properties;
        this.required = builder.required;
    }

    public String getType() {
        return this.type;
    }

    public Map<String, Map<String, Object>> getProperties() {
        return this.properties;
    }

    public List<String> getRequired() {
        return this.required;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.type);
        hash = 31 * hash + Objects.hashCode(this.properties);
        hash = 31 * hash + Objects.hashCode(this.required);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiParameters other = (MistralAiParameters) obj;
        return Objects.equals(this.type, other.type)
                && Objects.equals(this.properties, other.properties)
                && Objects.equals(this.required, other.required);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiParameters [", "]")
                .add("type=" + this.getType())
                .add("properties=" + this.getProperties())
                .add("required=" + this.getRequired())
                .toString();
    }

    public static MistralAiParameters.MistralAiParametersBuilder builder() {
        return new MistralAiParameters.MistralAiParametersBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class MistralAiParametersBuilder {
        // Defaulted here rather than in build(): a codec that binds the builder's fields directly,
        // as Jackson 3 does through the @JsonCreator below, never calls build().
        private String type = "object";
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        private MistralAiParametersBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiParametersBuilder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiParametersBuilder properties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiParametersBuilder required(List<String> required) {
            this.required = required;
            return this;
        }

        public MistralAiParameters build() {
            return new MistralAiParameters(this);
        }
    }
}
