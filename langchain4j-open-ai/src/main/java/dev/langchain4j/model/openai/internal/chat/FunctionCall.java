package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.Objects;

@JsonDeserialize(builder = FunctionCall.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionCall {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String arguments;

    @JsonCreator
    public FunctionCall(Builder builder) {
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    public String name() {
        return name;
    }

    public String arguments() {
        return arguments;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof FunctionCall
                && equalTo((FunctionCall) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(FunctionCall another) {
        return Objects.equals(name, another.name)
                && Objects.equals(arguments, another.arguments);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(arguments);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "FunctionCall{"
                + "name=" + name
                + ", arguments=" + arguments
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Builder {

        private String name;
        private String arguments;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public FunctionCall build() {
            return new FunctionCall(this);
        }
    }
}
