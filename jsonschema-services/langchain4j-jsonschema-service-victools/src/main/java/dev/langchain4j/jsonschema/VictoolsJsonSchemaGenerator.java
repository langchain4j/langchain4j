package dev.langchain4j.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.SchemaGenerator;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.exception.JsonSchemaGenerationException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JsonSchemaGenerator implementation that uses {@link com.github.victools.jsonschema.generator}.
 *
 * <p>This class generates JSON Schema for any given Java type that is correctly annotated with
 * {@link com.fasterxml.jackson.annotation}s.
 *
 * @see <a
 *     href="https://github.com/victools/jsonschema-generator/tree/main/jsonschema-module-jackson">JSON
 *     Schema Generation - Module jackson</a>
 */
public class VictoolsJsonSchemaGenerator implements JsonSchemaService.JsonSchemaGenerator {

    private final SchemaGenerator generator;
    private final ObjectMapper mapper = new ObjectMapper();

    public VictoolsJsonSchemaGenerator(
            com.github.victools.jsonschema.generator.SchemaGeneratorConfig config) {
        this.generator = new SchemaGenerator(config);
    }

    @Override
    public JsonSchema generate(Type type) throws JsonSchemaGenerationException {
        ObjectNode rawJsonSchema = this.generator.generateSchema(type);
        try {
            List<JsonSchemaProperty> properties =
                    rawJsonSchema.properties().stream()
                            .map(this::toJsonSchemaProperty)
                            .collect(Collectors.toList());
            return new JsonSchema(properties, type);
        } catch (IllegalArgumentException e) {
            throw new JsonSchemaGenerationException(e);
        }
    }

    /**
     * Convert named {@link JsonNode} to a {@link JsonSchemaProperty}.
     *
     * @param property an entry with the property name as the key and {@link JsonNode} as the value.
     * @return the {@link JsonSchemaProperty}.
     */
    private JsonSchemaProperty toJsonSchemaProperty(Map.Entry<String, JsonNode> property) {
        return JsonSchemaProperty.from(
                property.getKey(), mapper.convertValue(property.getValue(), Object.class));
    }
}
