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

@JsonDeserialize(builder = Tool.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Tool {

    @JsonProperty
    private final ToolType type = FUNCTION;
    @JsonProperty
    private final Function function;

    public Tool(Builder builder) {
        this.function = builder.function;
    }

    public ToolType type() {
        return this.type;
    }

    public Function function() {
        return this.function;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Tool
                && equalTo((Tool) another);
    }

    private boolean equalTo(Tool another) {
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
        return "Tool{"
                + "type=" + type
                + ", function=" + function
                + "}";
    }

    public static Tool from(Function function) {
        return new Builder()
                .function(function)
                .build();
    }

    public static Builder builder() {
        return new Builder();
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

        public Tool build() {
            return new Tool(this);
        }
    }
}
