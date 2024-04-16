package dev.langchain4j.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;

import java.lang.reflect.Type;

/**
 * JsonSchemaService implementation that uses {@link com.github.victools.jsonschema.generator}.
 *
 * <p>This class supports JSON Schema generation, serialization and deserialization for any given
 * Java type that is correctly annotated with {@link com.fasterxml.jackson.annotation}s.
 *
 * @see <a
 *     href="https://github.com/victools/jsonschema-generator/tree/main/jsonschema-module-jackson">JSON
 *     Schema Generation - Module jackson</a>
 */
public class VictoolsJsonSchemaService extends JsonSchemaService<JsonNode> {

    private final SchemaGeneratorConfig config;

    public VictoolsJsonSchemaService(SchemaGeneratorConfig config) {
        this.config = config;
    }

    @Override
    public JsonSchemaGenerator generator() {
        return new VictoolsJsonSchemaGenerator(this.config);
    }

    @Override
    public JsonSchemaSerde<JsonNode> serde() {
        return new JacksonJsonSchemaSerde();
    }

    @Override
    public JsonSchemaSanitizer<JsonNode> sanitizer() {
        return new JsonSchemaSanitizer<JsonNode>() {
            @Override
            public JsonNode sanitize(JsonNode parsedArgument, Type type) {
                return parsedArgument;
            }
        };
    }
}
