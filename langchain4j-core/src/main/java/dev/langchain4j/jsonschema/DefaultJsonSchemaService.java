package dev.langchain4j.jsonschema;

import com.google.gson.JsonElement;

/**
 * A default implementation of {@link JsonSchemaService} that uses {@link
 * DefaultJsonSchemaGenerator}, {@link DefaultJsonSchemaSerde}, and {@link
 * DefaultJsonSchemaSanitizer}.
 *
 * <p>This implementation supports JSON Schemas only for Java Built-in types.
 */
public class DefaultJsonSchemaService extends JsonSchemaService<JsonElement> {

    private final DefaultJsonSchemaGenerator schemaGenerator = new DefaultJsonSchemaGenerator();
    private final DefaultJsonSchemaSerde schemaSerde = new DefaultJsonSchemaSerde();
    private final DefaultJsonSchemaSanitizer schemaSanitizer;

    private DefaultJsonSchemaService(Builder builder) {
        this.schemaSanitizer = DefaultJsonSchemaSanitizer.builder().strict(builder.strict).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public JsonSchemaGenerator generator() {
        return schemaGenerator;
    }

    @Override
    public JsonSchemaSerde<JsonElement> serde() {
        return schemaSerde;
    }

    @Override
    public JsonSchemaSanitizer<JsonElement> sanitizer() {
        return schemaSanitizer;
    }

    public static class Builder {
        private boolean strict = true;

        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        public DefaultJsonSchemaService build() {
            return new DefaultJsonSchemaService(this);
        }
    }
}
