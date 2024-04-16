package dev.langchain4j.jsonschema;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agent.tool.JsonSchemaProperty;

import lombok.Getter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a JSON schema.
 *
 * <p>Each {@link JsonSchema} is like a map that defines a type's structure, used for defining and
 * validating data structures.
 *
 * <p>JSON Schema can describe tools or functions, not just types. OpenAI's tool/function calling
 * API needs tools defined with a JSON schema. The JSON schema for a tool is currently represented
 * as {@link dev.langchain4j.agent.tool.ToolSpecification}, which includes {@link
 * JsonSchemaProperty}s and is widely used in the project. Therefore, this class only represents
 * types and is based on {@link JsonSchemaProperty}s to stay compatible with existing codebase.
 */
@Getter
public class JsonSchema {

    /**
     * The properties of the schema, which may include:
     *
     * <ul>
     *   <li>"type" property, whose value could be one of "string", "integer", "boolean", "number",
     *       "array", or "object".
     *   <li>"description" property, whose value is a string describing the schema.
     *   <li>Other properties that describe the schema.
     * </ul>
     */
    private final List<JsonSchemaProperty> properties;

    /**
     * The type that the schema is generated from. This is used to keep track of the original type
     */
    private final Type originalType;

    /** Creates a new {@link JsonSchema} with the given properties. */
    public JsonSchema(List<JsonSchemaProperty> properties, Type originalType) {
        this.properties = properties;
        this.originalType = originalType;
    }

    /**
     * Add the description property.
     *
     * <p>If a description property already exists, it will be added to the beginning of the
     * original one. Otherwise, a new description property will be inserted after the type property.
     *
     * @param description
     * @return
     */
    public JsonSchema addDescription(String description) {
        if (isNullOrBlank(description)) {
            return this;
        }

        JsonSchemaProperty typeProperty = null;
        JsonSchemaProperty descriptionProperty = null;
        List<JsonSchemaProperty> otherProperties = new ArrayList<>();

        for (JsonSchemaProperty property : this.properties) {
            switch (property.key()) {
                case "type":
                    typeProperty = property;
                    continue;
                case "description":
                    if (!isNullOrBlank((String) property.value())) {
                        description += "\n" + property.value();
                    }
                    descriptionProperty = JsonSchemaProperty.description(description);
                    continue;
                default:
                    otherProperties.add(property);
            }
        }
        if (descriptionProperty == null) {
            descriptionProperty = JsonSchemaProperty.description(description);
        }

        return new JsonSchema(
                Stream.concat(
                                Stream.of(typeProperty, descriptionProperty),
                                otherProperties.stream())
                        .collect(Collectors.toList()),
                this.originalType);
    }
}
