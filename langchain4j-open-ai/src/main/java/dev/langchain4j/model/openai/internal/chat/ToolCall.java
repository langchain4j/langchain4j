package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ToolCall.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ToolCall {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final Integer index;
    @JsonProperty
    private final ToolType type;
    @JsonProperty
    private final FunctionCall function;

    public ToolCall(Builder builder) {
        this.id = builder.id;
        this.index = builder.index;
        this.type = builder.type;
        this.function = builder.function;
    }

    public String id() {
        return id;
    }

    public Integer index() {
        return index;
    }

    public ToolType type() {
        return type;
    }

    public FunctionCall function() {
        return function;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolCall
                && equalTo((ToolCall) another);
    }

    private boolean equalTo(ToolCall another) {
        return Objects.equals(id, another.id)
                && Objects.equals(index, another.index)
                && Objects.equals(type, another.type)
                && Objects.equals(function, another.function);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(id);
        h += (h << 5) + Objects.hashCode(index);
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(function);
        return h;
    }

    @Override
    public String toString() {
        return "ToolCall{"
                + "id=" + id
                + ", index=" + index
                + ", type=" + type
                + ", function=" + function
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String id;
        private Integer index;
        private ToolType type;
        private FunctionCall function;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder type(ToolType type) {
            this.type = type;
            return this;
        }

        public Builder function(FunctionCall function) {
            this.function = function;
            return this;
        }

        public ToolCall build() {
            return new ToolCall(this);
        }
    }
}
