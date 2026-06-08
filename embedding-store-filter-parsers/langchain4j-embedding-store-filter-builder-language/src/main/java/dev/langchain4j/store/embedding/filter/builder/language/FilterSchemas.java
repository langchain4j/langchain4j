package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.model.chat.request.json.*;
import java.util.List;
import java.util.Map;

/**
 * Provides JSON Schema definitions for LangChain4j Filter generation.
 * 
 * <p>This class creates JSON Schema structures that guide language models
 * in generating properly formatted filter JSON that can be parsed into
 * LangChain4j Filter objects. The schemas use references and anyOf constructs
 * to support the full range of filter types while maintaining compatibility
 * with the LangChain4j API.
 * 
 * <p>The schemas are designed for use with ResponseFormat.JSON to ensure
 * structured outputs from language models. They include:
 * <ul>
 *   <li>Comparison filters (equals, greater than, contains, etc.)</li>
 *   <li>Set filters (in, not in)</li>
 *   <li>Logical filters (and, or, not) with recursive nesting</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * JsonSchema schema = JsonSchema.builder()
 *     .name("filter_schema")
 *     .rootElement(FilterSchemas.createRequestSchema())
 *     .build();
 * }</pre>
 * 
 * @since 1.0.0
 */
public class FilterSchemas {
        
    /**
     * Creates the root object schema for filter requests.
     * 
     * <p>This schema defines the structure expected from language models,
     * containing both a filter object and a modified query. The schema
     * includes reference definitions for nested filter structures.
     * 
     * @return the root JSON schema for filter requests
     */
    public static JsonObjectSchema createRequestSchema() {
        return JsonObjectSchema.builder()
            .addProperty("filter", JsonReferenceSchema.builder()
                .reference("#/$defs/Filter")
                .build())
            .addStringProperty("modifiedQuery", "The modified query for semantic search, focusing on content rather than metadata constraints")
            .required("filter", "modifiedQuery")
            .description("Request containing a filter object and modified query for hybrid search")
            .definitions(Map.of(
                "Filter", createFilterSchema(),
                "Value", createValueSchema()
            ))
            .build();
    }
        
    /**
     * Creates the main filter schema supporting all LangChain4j filter types.
     * 
     * <p>This schema uses anyOf to allow the language model to choose between
     * different filter types including comparison, set, and logical operations.
     * The schema supports recursive nesting for complex logical expressions.
     * 
     * @return the JSON schema element for filter definitions
     */
    public static JsonSchemaElement createFilterSchema() {
        return JsonAnyOfSchema.builder()
            .anyOf(List.of(
                createComparisonFilterSchema(),
                createSetFilterSchema(),
                createAndFilterSchema(),
                createOrFilterSchema(),
                createNotFilterSchema()
            ))
            .description("A filter that can be one of comparison, set, or logical operations")
            .build();
    }
        
    /**
     * Creates a schema for primitive value types.
     * 
     * <p>This schema defines the acceptable value types for filter operations,
     * including strings, numbers, and booleans. It uses anyOf to allow
     * the language model flexibility in value type selection.
     * 
     * @return the JSON schema element for value types
     */
    public static JsonSchemaElement createValueSchema() {
        return JsonAnyOfSchema.builder()
            .anyOf(List.of(
                JsonStringSchema.builder().description("String value").build(),
                JsonNumberSchema.builder().description("Numeric value").build(),
                JsonBooleanSchema.builder().description("Boolean value").build()
            ))
            .description("Value can be string, number, or boolean")
            .build();
    }
    
    /**
     * Creates comparison filter schema (equals, not equals, greater than, etc.)
     */
    private static JsonSchemaElement createComparisonFilterSchema() {
        return JsonObjectSchema.builder()
            .addEnumProperty("type", List.of(
                "IS_EQUAL_TO", 
                "IS_NOT_EQUAL_TO", 
                "IS_GREATER_THAN", 
                "IS_GREATER_THAN_OR_EQUAL_TO",
                "IS_LESS_THAN", 
                "IS_LESS_THAN_OR_EQUAL_TO",
                "CONTAINS_STRING"
            ), "The type of comparison operation")
            .addStringProperty("key", "The metadata key to filter on")
            .addProperty("value", JsonReferenceSchema.builder()
                .reference("#/$defs/Value")
                .build())
            .required("type", "key", "value")
            .build();
    }
    
    /**
     * Creates set filter schema (in, not in)
     */
    private static JsonSchemaElement createSetFilterSchema() {
        return JsonObjectSchema.builder()
            .addEnumProperty("type", List.of("IS_IN", "IS_NOT_IN"), "The type of set operation")
            .addStringProperty("key", "The metadata key to filter on")
            .addProperty("values", JsonArraySchema.builder()
                .items(JsonReferenceSchema.builder()
                    .reference("#/$defs/Value")
                    .build())
                .description("Array of values for set operations")
                .build())
            .required("type", "key", "values")
            .build();
    }
    
    /**
     * Creates logical AND filter schema
     */
    private static JsonSchemaElement createAndFilterSchema() {
        return JsonObjectSchema.builder()
            .addEnumProperty("type", List.of("AND"), "Logical AND operation")
            .addProperty("left", JsonReferenceSchema.builder()
                .reference("#/$defs/Filter")
                .build())
            .addProperty("right", JsonReferenceSchema.builder()
                .reference("#/$defs/Filter")
                .build())
            .required("type", "left", "right")
            .build();
    }
    
    /**
     * Creates logical OR filter schema
     */
    private static JsonSchemaElement createOrFilterSchema() {
        return JsonObjectSchema.builder()
            .addEnumProperty("type", List.of("OR"), "Logical OR operation")
            .addProperty("left", JsonReferenceSchema.builder()
                .reference("#/$defs/Filter")
                .build())
            .addProperty("right", JsonReferenceSchema.builder()
                .reference("#/$defs/Filter")
                .build())
            .required("type", "left", "right")
            .build();
    }
    
    /**
     * Creates logical NOT filter schema
     */
    private static JsonSchemaElement createNotFilterSchema() {
        return JsonObjectSchema.builder()
            .addEnumProperty("type", List.of("NOT"), "Logical NOT operation")
            .addProperty("filter", JsonReferenceSchema.builder()
                .reference("#/$defs/Filter")
                .build())
            .required("type", "filter")
            .build();
    }        
}