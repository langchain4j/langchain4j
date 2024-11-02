package dev.langchain4j.model.chat.request.json;

import com.fasterxml.jackson.core.JsonParseException;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class JsonSchemaParserTest {

    @Test
    public void testParseInvalidJsonSchema() {
        String jsonSchema = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "age": {
                    "type": "integer"
                }
                // Missing comma here should trigger an error
                "email": {
                    "type": "string"
                }
            }
        }
    """;

        try {
            JsonSchemaParser.fromJsonString(jsonSchema);
            fail("Expected a JsonParseException to be thrown");
        } catch (RuntimeException e) {
            assertInstanceOf(JsonParseException.class, e.getCause());
        }
    }


    @Test
    public void testParseSimpleJsonString() {
        String jsonSchema = """
            {
                "type": "string",
                "description": "A simple string"
            }
        """;

        ResponseFormat responseFormat = JsonSchemaParser.fromJsonString(jsonSchema);
        assertEquals(responseFormat.type(), ResponseFormatType.JSON);
        assertInstanceOf(JsonStringSchema.class, responseFormat.jsonSchema().rootElement());
        assertEquals(((JsonStringSchema) responseFormat.jsonSchema().rootElement()).description(), "A simple string");
    }


    @Test
    public void testParseNestedJsonObject() {
        String jsonSchema = """
        {
            "type": "object",
            "properties": {
                "person": {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string"
                        },
                        "age": {
                            "type": "integer"
                        }
                    },
                    "required": ["name"]
                }
            },
            "required": ["person"]
        }
    """;

        ResponseFormat responseFormat = JsonSchemaParser.fromJsonString(jsonSchema);
        assertEquals(responseFormat.type(), ResponseFormatType.JSON);
        assertInstanceOf(JsonObjectSchema.class, responseFormat.jsonSchema().rootElement());
        JsonObjectSchema rootSchema = (JsonObjectSchema) responseFormat.jsonSchema().rootElement();

        assertEquals(1, rootSchema.properties().size());
        assertInstanceOf(JsonObjectSchema.class, rootSchema.properties().get("person"));
        JsonObjectSchema personSchema = (JsonObjectSchema) rootSchema.properties().get("person");
        assertEquals(2, personSchema.properties().size());
        assertEquals(1, personSchema.required().size());
        assertInstanceOf(JsonStringSchema.class, personSchema.properties().get("name"));
        assertInstanceOf(JsonIntegerSchema.class, personSchema.properties().get("age"));
    }




    @Test
    public void testParseComplexJsonString() {
        String jsonSchema = """
            {
                    "type": "object",
                    "properties": {
                        "title": {
                            "type": "string"
                        },
                        "preparationTimeMinutes": {
                            "description": "A preparation time minutes",
                            "type": "integer"
                        },
                        "ingredients": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "steps": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        }
                    },
                    "required": [
                        "title",
                        "preparationTimeMinutes",
                        "steps"
                    ]
            }
        """;

        ResponseFormat responseFormat = JsonSchemaParser.fromJsonString(jsonSchema);
        assertEquals(responseFormat.type(), ResponseFormatType.JSON);
        assertInstanceOf(JsonObjectSchema.class, responseFormat.jsonSchema().rootElement());
        assertEquals(4, ((JsonObjectSchema)responseFormat.jsonSchema().rootElement()).properties().size());
        assertEquals(3, ((JsonObjectSchema)responseFormat.jsonSchema().rootElement()).required().size());
        assertInstanceOf(JsonStringSchema.class, ((JsonObjectSchema)responseFormat.jsonSchema().rootElement()).properties().get("title"));
        assertInstanceOf(JsonIntegerSchema.class, ((JsonObjectSchema)responseFormat.jsonSchema().rootElement()).properties().get("preparationTimeMinutes"));
        assertEquals("A preparation time minutes", ((JsonIntegerSchema)((JsonObjectSchema)responseFormat.jsonSchema().rootElement()).properties().get("preparationTimeMinutes")).description());
    }

}
