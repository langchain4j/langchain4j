package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ResponseFormat.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ResponseFormat {

    @JsonProperty
    private final ResponseFormatType type;
    @JsonProperty
    private final JsonSchema jsonSchema;

    @JsonCreator
    public ResponseFormat(Builder builder) {
        this.type = builder.type;
        this.jsonSchema = builder.jsonSchema;
    }

    public ResponseFormatType type() {
        return type;
    }

    public JsonSchema jsonSchema() {
        return jsonSchema;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ResponseFormat
                && equalTo((ResponseFormat) another);
    }

    private boolean equalTo(ResponseFormat another) {
        return Objects.equals(type, another.type)
                && Objects.equals(jsonSchema, another.jsonSchema);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(jsonSchema);
        return h;
    }

    @Override
    public String toString() {
        return "ResponseFormat{" +
                "type=" + type +
                ", jsonSchema=" + jsonSchema +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private ResponseFormatType type;
        private JsonSchema jsonSchema;

        public Builder type(ResponseFormatType type) {
            this.type = type;
            return this;
        }

        public Builder jsonSchema(JsonSchema jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        public ResponseFormat build() {
            return new ResponseFormat(this);
        }
    }
}
