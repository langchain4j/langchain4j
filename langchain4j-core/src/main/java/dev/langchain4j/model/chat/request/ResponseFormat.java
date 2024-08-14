package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@Experimental
public class ResponseFormat {

    private final ResponseFormatType type;
    private final JsonSchema jsonSchema;

    private ResponseFormat(Builder builder) {
        this.type = ensureNotNull(builder.type, "type");
        this.jsonSchema = builder.jsonSchema;
        if (jsonSchema != null && type != JSON) {
            throw new IllegalStateException("JsonSchema can be specified only when type=JSON");
        }
    }

    public ResponseFormatType type() {
        return type;
    }

    public JsonSchema jsonSchema() {
        return jsonSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseFormat that = (ResponseFormat) o;
        return Objects.equals(this.type, that.type)
                && Objects.equals(this.jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, jsonSchema);
    }

    @Override
    public String toString() {
        return "ResponseFormat {" +
                " type = " + type +
                ", jsonSchema = " + jsonSchema +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

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
