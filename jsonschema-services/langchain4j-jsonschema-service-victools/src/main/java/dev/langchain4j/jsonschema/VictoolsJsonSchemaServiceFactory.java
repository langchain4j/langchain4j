package dev.langchain4j.jsonschema;

import static dev.langchain4j.jsonschema.JsonSchemaServiceFactories.*;

import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import dev.langchain4j.spi.jsonschema.JsonSchemaServiceFactory;

import java.util.Map;

public class VictoolsJsonSchemaServiceFactory implements JsonSchemaServiceFactory {

    /**
     * Create a new {@link Service}.
     *
     * <p>The service is configured to generate JsonSchema with respects to Jackson annotations. In
     * addition, the generator is configured to suit the needs of the Langchain4j project.
     */
    @Override
    public Service create() {
        return new VictoolsJsonSchemaService(getSchemaGeneratorConfig());
    }

    /** Adjust the configuration of the schema generator for the Langchain4j project. */
    static SchemaGeneratorConfig getSchemaGeneratorConfig() {
        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12);

        // Enable the Jackson module
        configBuilder
                .with(
                        new JacksonModule(
                                // Make the generator respect the order of the properties
                                JacksonOption.RESPECT_JSONPROPERTY_ORDER,
                                // Make the generator respect the required flag of the properties
                                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED))
                // Define the value type of Map to the additionalProperties attribute.
                // Hence, a Map type like {@code Map<String, Integer>} will be converted to schema:
                // { "type": "object", "additionalProperties": { "type": "integer" } }
                .with(Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES)
                .without(
                        // Take very object field as nonnull by default
                        Option.NULLABLE_FIELDS_BY_DEFAULT,
                        // Do not generate a '$defs' attribute at the root of the schema
                        Option.DEFINITIONS_FOR_ALL_OBJECTS);

        configBuilder
                .forTypesInGeneral()
                // Remove the 'properties' attribute from the schema for Map types,
                // as Victools puts the methods of the Map interface as properties in the schema.
                .withTypeAttributeOverride(
                        (attributes, scope, context) -> {
                            if (scope.getType().isInstanceOf(Map.class)) {
                                attributes.remove("properties");
                            }
                        });
        return configBuilder.build();
    }
}
