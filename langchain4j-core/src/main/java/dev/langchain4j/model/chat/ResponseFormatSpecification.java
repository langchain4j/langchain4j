package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.output.structured.json.JsonSchema;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class ResponseFormatSpecification {

    private final ResponseFormat responseFormat;
    private final JsonSchema jsonSchema;

    private ResponseFormatSpecification(Builder builder) {
        this.responseFormat = ensureNotNull(builder.responseFormat, "responseFormat");
        this.jsonSchema = builder.jsonSchema;
    }

    public ResponseFormat responseFormat() {
        return responseFormat;
    }

    public JsonSchema jsonSchema() {
        return jsonSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseFormatSpecification that = (ResponseFormatSpecification) o;
        return Objects.equals(this.responseFormat, that.responseFormat)
                && Objects.equals(this.jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseFormat, jsonSchema);
    }

    @Override
    public String toString() {
        return "ResponseFormatSpecification {" +
                " responseFormat = " + responseFormat +
                ", jsonSchema = " + jsonSchema +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ResponseFormat responseFormat;
        private JsonSchema jsonSchema;

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder jsonSchema(JsonSchema jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        public ResponseFormatSpecification build() {
            return new ResponseFormatSpecification(this);
        }
    }
}
