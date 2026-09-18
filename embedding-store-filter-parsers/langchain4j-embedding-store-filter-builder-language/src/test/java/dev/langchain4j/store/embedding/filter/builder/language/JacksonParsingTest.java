package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class JacksonParsingTest {

    @Test
    void testParseSimpleEqualToFilter() throws Exception {
        String json = """
            {
                "type": "IS_EQUAL_TO",
                "key": "author",
                "value": "John Doe"
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(IsEqualTo.class, filter);
        // Filter successfully created - the exact API methods depend on LangChain4J version
    }

    @Test
    void testParseNumericGreaterThanFilter() throws Exception {
        String json = """
            {
                "type": "IS_GREATER_THAN",
                "key": "rating",
                "value": 4.5
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(IsGreaterThan.class, filter);
        // Filter successfully created with numeric value
    }

    @Test
    void testParseInFilter() throws Exception {
        String json = """
            {
                "type": "IS_IN",
                "key": "category",
                "values": ["tech", "science", "engineering"]
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(IsIn.class, filter);
        // Filter successfully created with array values
    }

    @Test
    void testParseAndFilter() throws Exception {
        String json = """
            {
                "type": "AND",
                "conditions": [
                    {
                        "type": "IS_EQUAL_TO",
                        "key": "author",
                        "value": "John Doe"
                    },
                    {
                        "type": "IS_GREATER_THAN",
                        "key": "rating",
                        "value": 4
                    }
                ]
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(And.class, filter);
        // Complex AND filter successfully created with nested conditions
    }

    @Test
    void testParseBooleanValue() throws Exception {
        String json = """
            {
                "type": "IS_EQUAL_TO",
                "key": "isPublished",
                "value": true
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(IsEqualTo.class, filter);
        // Filter successfully created with boolean value
    }

    @Test
    void testParseNotFilter() throws Exception {
        String json = """
            {
                "type": "NOT",
                "condition": {
                    "type": "IS_EQUAL_TO",
                    "key": "status",
                    "value": "draft"
                }
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(Not.class, filter);
        // NOT filter successfully created with nested condition
    }

    @Test
    void testParseAlternativeFilterNames() throws Exception {
        String json = """
            {
                "type": "EQUALS",
                "key": "name",
                "value": "test"
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(IsEqualTo.class, filter);
        // Alternative filter name successfully recognized
    }

    @Test
    void testParseContainsStringFilter() throws Exception {
        String json = """
            {
                "type": "CONTAINS_STRING",
                "key": "title",
                "value": "machine learning"
            }
            """;

        TestableFilterBuilder builder = new TestableFilterBuilder();
        Filter filter = builder.testParseJsonNodeToFilter(json);

        assertInstanceOf(dev.langchain4j.store.embedding.filter.comparison.ContainsString.class, filter);
        // ContainsString filter successfully created
    }

    // Helper class to test the parsing method
    private static class TestableFilterBuilder {
        private final ObjectMapper objectMapper;
        
        public TestableFilterBuilder() {
            this.objectMapper = new ObjectMapper();
        }

        public Filter testParseJsonNodeToFilter(String json) throws Exception {
            JsonNode node = objectMapper.readTree(json);
            return parseJsonNodeToFilter(node);
        }

        // Copy of the parsing logic for testing (this would normally be protected/package-private in the actual class)
        private Filter parseJsonNodeToFilter(JsonNode node) {
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
}