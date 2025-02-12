package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static dev.langchain4j.model.openai.internal.chat.ToolType.FUNCTION;

@JsonDeserialize(builder = ToolChoice.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ToolChoice {

    @JsonProperty
    private final ToolType type = FUNCTION;
    @JsonProperty
    private final Function function;

    public ToolChoice(Builder builder) {
        function = builder.function;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolChoice
                && equalTo((ToolChoice) another);
    }

    private boolean equalTo(ToolChoice another) {
        return Objects.equals(type, another.type)
                && Objects.equals(function, another.function);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(function);
        return h;
    }

    @Override
    public String toString() {
        return "ToolChoice{" +
                "type=" + type +
                ", function=" + function +
                "}";
    }

    public static ToolChoice from(String functionName) {
        return new Builder()
                .function(Function.builder()
                        .name(functionName).build())
                .build();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Function function;

        public Builder function(Function function) {
            this.function = function;
            return this;
        }

        public ToolChoice build() {
            return new ToolChoice(this);
        }
    }
}
