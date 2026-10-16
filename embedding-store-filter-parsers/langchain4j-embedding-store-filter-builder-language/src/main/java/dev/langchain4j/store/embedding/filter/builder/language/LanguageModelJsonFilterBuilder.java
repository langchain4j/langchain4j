package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * A filter builder that uses a language model to convert natural language queries
 * into structured LangChain4j Filter objects.
 * 
 * <p>This class processes natural language queries by:
 * <ul>
 *   <li>Separating metadata constraints from semantic content</li>
 *   <li>Converting metadata constraints into structured Filter objects</li>
 *   <li>Producing a modified query focused on semantic content</li>
 * </ul>
 * 
 * <p>The builder uses JSON Schema-based structured outputs to ensure reliable
 * filter generation from language models that support ResponseFormat.JSON.
 * 
 * <p>Example usage:
 * <pre>{@code
 * TableDefinition tableDefinition = TableDefinition.builder()
 *     .addColumn("author", String.class, "Document author")
 *     .addColumn("rating", Number.class, "User rating from 1-5")
 *     .build();
 * 
 * LanguageModelJsonFilterBuilder builder = new LanguageModelJsonFilterBuilder(
 *     chatModel, tableDefinition);
 * 
 * FilterResult result = builder.buildFilterAndQuery(
 *     "Find documents by John Doe with rating above 4");
 * 
 * Filter filter = result.getFilter(); // IsEqualTo("author", "John Doe") AND IsGreaterThan("rating", 4)
 * String query = result.getModifiedQuery(); // "documents"
 * }</pre>
 * 
 * @since 1.0.0
 */
public class LanguageModelJsonFilterBuilder {

    private final ChatModel chatModel;
    private final TableDefinition tableDefinition;
    private final ObjectMapper objectMapper;

    private static final PromptTemplate FILTER_PROMPT = PromptTemplate.from("""
            You are a hybrid search query processor that separates structured metadata constraints from semantic content.
            
            Current date: {{currentDate}}
            
            Available metadata columns:
            {{columns}}
            
            User query: {{query}}
            
            You must return a JSON object with exactly this structure:
            {
              "filter": <filter_object_or_null>,
              "modifiedQuery": "string"
            }
            
            Filter Guidelines:
            - Use filters for exact metadata constraints: dates, authors, categories, ratings, status, etc.
            - Return null for filter if no metadata constraints are needed
            - Valid filter types: IS_EQUAL_TO, IS_NOT_EQUAL_TO, IS_GREATER_THAN, IS_GREATER_THAN_OR_EQUAL_TO, IS_LESS_THAN, IS_LESS_THAN_OR_EQUAL_TO, CONTAINS_STRING, IS_IN, IS_NOT_IN, AND, OR, NOT
            - For relative date queries, calculate actual dates based on current date above
            - Use YYYY-MM-DD format for dates
            
            Examples of valid filters:
            {"type": "IS_EQUAL_TO", "key": "author", "value": "John Doe"}
            {"type": "IS_GREATER_THAN", "key": "rating", "value": 4}
            {"type": "AND", "conditions": [{"type": "IS_EQUAL_TO", "key": "author", "value": "John"}, {"type": "IS_GREATER_THAN", "key": "rating", "value": 3}]}
            
            Modified Query Guidelines:
            - Focus on semantic content, topics, concepts, and subject matter
            - Remove metadata constraints that are handled by the filter
            - Keep content-related keywords for embedding search
            
            IMPORTANT: Return actual data, not schema definitions. Use the exact structure shown above.
            """);

    /**
     * Constructs a new LanguageModelJsonFilterBuilder.
     * 
     * @param chatModel the language model to use for filter generation.
     *                  Must support ResponseFormat.JSON for structured outputs.
     * @param tableDefinition the definition of available metadata columns
     *                       that can be used in filters
     * @throws NullPointerException if chatModel or tableDefinition is null
     */
    public LanguageModelJsonFilterBuilder(ChatModel chatModel, TableDefinition tableDefinition) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.tableDefinition = Objects.requireNonNull(tableDefinition, "tableDefinition cannot be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Builds a filter and modified query from a natural language query.
     * 
     * <p>This method processes the input query to:
     * <ul>
     *   <li>Extract metadata constraints and convert them to Filter objects</li>
     *   <li>Remove metadata constraints from the query to focus on semantic content</li>
     *   <li>Return both the structured filter and the modified query</li>
     * </ul>
     * 
     * <p>The language model is provided with the current date context to handle
     * relative date queries like "in the last week" or "this month".
     * 
     * @param naturalLanguageQuery the natural language query to process.
     *                            Should contain both semantic content and metadata constraints.
     * @return a FilterResult containing the extracted filter and modified query
     * @throws NullPointerException if naturalLanguageQuery is null
     * @throws IllegalArgumentException if the language model returns invalid JSON
     *                                 or if the response cannot be parsed
     */
    public FilterResult buildFilterAndQuery(String naturalLanguageQuery) {
        Objects.requireNonNull(naturalLanguageQuery, "naturalLanguageQuery cannot be null");

        String columnsDescription = buildColumnsDescription();
        
        // Add current date context
        java.time.LocalDate today = java.time.LocalDate.now();
        
        Prompt prompt = FILTER_PROMPT.apply(Map.of(
                "currentDate", today.toString(),
                "columns", columnsDescription,
                "query", naturalLanguageQuery
        ));

        // Use JSON mode for structured outputs with schema
        ChatRequest request = ChatRequest.builder()
                .messages(prompt.toUserMessage())
                .responseFormat(ResponseFormat.builder()
                    .type(JSON)
                    .jsonSchema(JsonSchema.builder()
                        .name("filter_schema")
                        .rootElement(FilterSchemas.createRequestSchema())
                        .build())
                    .build())
                .build();
        
        String response = chatModel.chat(request).aiMessage().text();
        
        // Parse the structured JSON response
        return parseStructuredResponse(response);
    }

    private String buildColumnsDescription() {
        return tableDefinition.getColumns().stream()
                .map(col -> {
                    String baseDescription = String.format("- %s (%s): %s", 
                            col.getName(), 
                            col.getTypeName(), 
                            col.getDescription() != null ? col.getDescription() : "");
                    
                    if (col.isEnum()) {
                        return baseDescription + " [possible values: " + String.join(", ", col.getEnumValueNames()) + "]";
                    }
                    return baseDescription;
                })
                .collect(Collectors.joining("\n"));
    }

    private FilterResult parseStructuredResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Extract the filter from the wrapper object
            JsonNode filterNode = rootNode.get("filter");
            Filter filter = getFilter(filterNode);
            
            // Extract the modified query
            JsonNode queryNode = rootNode.get("modifiedQuery");
            String modifiedQuery = queryNode != null ? queryNode.asText() : "";
            
            return new FilterResult(filter, modifiedQuery);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse response: " + jsonResponse, e);
        }
    }

    /**
     * Converts a JsonNode representing a filter into a LangChain4j Filter object.
     * 
     * <p>This method handles the conversion from the JSON representation returned
     * by the language model into the appropriate Filter implementation. It supports
     * all standard LangChain4j filter types including comparison, set, and logical operations.
     * 
     * <p>The method includes validation to detect if the language model returned
     * a schema definition instead of actual filter data, which can happen with
     * some models when the prompt is not sufficiently clear.
     * 
     * @param filterNode the JSON node containing the filter definition,
     *                   or null if no filtering is needed
     * @return the corresponding Filter object, or null if filterNode is null
     * @throws IllegalArgumentException if the JSON structure is invalid,
     *                                 contains schema definitions instead of data,
     *                                 or represents an unsupported filter type
     */
    public Filter getFilter(JsonNode filterNode) {
        Filter filter = null;
        if (filterNode != null && !filterNode.isNull()) {
            // Check if this looks like a malformed schema instead of data
            if (filterNode.has("properties") || filterNode.has("enum")) {
                throw new IllegalArgumentException("LLM returned schema structure instead of data: " + filterNode);
            }
            filter = parseJsonNodeToFilter(filterNode);
        }
        return filter;
    }
    
    private Filter parseJsonNodeToFilter(JsonNode node) {
        if (!node.has("type")) {
            throw new IllegalArgumentException("Filter node missing required 'type' field: " + node);
        }
        String type = node.get("type").asText().toUpperCase();
        
        return switch (type) {
            case "IS_EQUAL_TO", "EQUALS" -> new IsEqualTo(
                node.get("key").asText(),
                parseValue(node.get("value"))
            );
            case "IS_NOT_EQUAL_TO", "NOT_EQUALS" -> new IsNotEqualTo(
                node.get("key").asText(),
                parseValue(node.get("value"))
            );
            case "IS_GREATER_THAN", "GREATER_THAN" -> new IsGreaterThan(
                node.get("key").asText(),
                (Comparable<?>) parseValue(node.get("value"))
            );
            case "IS_GREATER_THAN_OR_EQUAL_TO", "GREATER_THAN_OR_EQUAL" -> new IsGreaterThanOrEqualTo(
                node.get("key").asText(),
                (Comparable<?>) parseValue(node.get("value"))
            );
            case "IS_LESS_THAN", "LESS_THAN" -> new IsLessThan(
                node.get("key").asText(),
                (Comparable<?>) parseValue(node.get("value"))
            );
            case "IS_LESS_THAN_OR_EQUAL_TO", "LESS_THAN_OR_EQUAL" -> new IsLessThanOrEqualTo(
                node.get("key").asText(),
                (Comparable<?>) parseValue(node.get("value"))
            );
            case "IS_IN", "IN" -> new IsIn(
                node.get("key").asText(),
                parseValueArray(node.get("values"))
            );
            case "IS_NOT_IN", "NOT_IN" -> new IsNotIn(
                node.get("key").asText(),
                parseValueArray(node.get("values"))
            );
            case "CONTAINS_STRING", "CONTAINS" -> new dev.langchain4j.store.embedding.filter.comparison.ContainsString(
                node.get("key").asText(),
                node.get("value").asText()
            );
            case "AND" -> {
                if (node.has("conditions")) {
                    JsonNode conditions = node.get("conditions");
                    if (conditions.size() < 2) {
                        throw new IllegalArgumentException("AND filter requires at least 2 conditions");
                    }
                    Filter result = parseJsonNodeToFilter(conditions.get(0));
                    for (int i = 1; i < conditions.size(); i++) {
                        result = new And(result, parseJsonNodeToFilter(conditions.get(i)));
                    }
                    yield result;
                } else {
                    yield new And(
                        parseJsonNodeToFilter(node.get("left")),
                        parseJsonNodeToFilter(node.get("right"))
                    );
                }
            }
            case "OR" -> {
                if (node.has("conditions")) {
                    JsonNode conditions = node.get("conditions");
                    if (conditions.size() < 2) {
                        throw new IllegalArgumentException("OR filter requires at least 2 conditions");
                    }
                    Filter result = parseJsonNodeToFilter(conditions.get(0));
                    for (int i = 1; i < conditions.size(); i++) {
                        result = new Or(result, parseJsonNodeToFilter(conditions.get(i)));
                    }
                    yield result;
                } else {
                    yield new Or(
                        parseJsonNodeToFilter(node.get("left")),
                        parseJsonNodeToFilter(node.get("right"))
                    );
                }
            }
            case "NOT" -> new Not(parseJsonNodeToFilter(node.get("condition")));
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }
    
    private Object parseValue(JsonNode valueNode) {
        if (valueNode.isTextual()) {
            return valueNode.asText();
        } else if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        } else if (valueNode.isIntegralNumber()) {
            return valueNode.asLong();
        } else if (valueNode.isFloatingPointNumber()) {
            return valueNode.asDouble();
        } else if (valueNode.isNull()) {
            return null;
        } else {
            return valueNode.asText(); // fallback to string
        }
    }
    
    private List<Object> parseValueArray(JsonNode arrayNode) {
        List<Object> values = new java.util.ArrayList<>();
        for (JsonNode valueNode : arrayNode) {
            values.add(parseValue(valueNode));
        }
        return values;
    }

}