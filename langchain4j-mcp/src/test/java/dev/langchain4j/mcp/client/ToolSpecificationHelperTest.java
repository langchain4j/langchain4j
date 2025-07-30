package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolSpecificationHelperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void toolWithSimpleParams() throws JsonProcessingException {
        String text =
                // language=json
                """
                [ {
                      "name" : "operation",
                      "description" : "Super operation",
                      "inputSchema" : {
                        "type" : "object",
                        "properties" : {
                          "stringParameter" : {
                            "type" : "string",
                            "description" : "Message to echo"
                          },
                          "enumParameter" : {
                            "type" : "string",
                            "description" : "The protocol to use",
                            "enum": [
                                "http",
                                "https"
                            ]
                          },
                          "numberParameter": {
                            "type": "number",
                            "description": "A number"
                          },
                          "integerParameter": {
                            "type": "integer",
                            "description": "An integer"
                          },
                          "booleanParameter": {
                            "type": "boolean",
                            "description": "A boolean"
                          },
                          "arrayParameter": {
                              "type": "array",
                              "description": "An array of strings",
                              "items": {
                                "type": "string"
                              }
                          }
                        },
                        "required" : [ "stringParameter" ],
                        "additionalProperties" : false,
                        "$schema" : "http://json-schema.org/draft-07/schema#"
                      }
                    } ]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("operation");
        assertThat(toolSpecification.description()).isEqualTo("Super operation");

        // validate parameters
        JsonObjectSchema parameters = toolSpecification.parameters();
        assertThat(parameters.properties()).hasSize(6);
        assertThat(parameters.required()).hasSize(1);
        assertThat(parameters.required().get(0)).isEqualTo("stringParameter");

        JsonStringSchema messageParameter =
                (JsonStringSchema) parameters.properties().get("stringParameter");
        assertThat(messageParameter.description()).isEqualTo("Message to echo");

        JsonEnumSchema enumParameter = (JsonEnumSchema) parameters.properties().get("enumParameter");
        assertThat(enumParameter.description()).isEqualTo("The protocol to use");
        assertThat(enumParameter.enumValues()).containsExactly("http", "https");

        JsonNumberSchema numberParameter =
                (JsonNumberSchema) parameters.properties().get("numberParameter");
        assertThat(numberParameter.description()).isEqualTo("A number");

        JsonIntegerSchema integerParameter =
                (JsonIntegerSchema) parameters.properties().get("integerParameter");
        assertThat(integerParameter.description()).isEqualTo("An integer");

        JsonBooleanSchema booleanParameter =
                (JsonBooleanSchema) parameters.properties().get("booleanParameter");
        assertThat(booleanParameter.description()).isEqualTo("A boolean");

        JsonArraySchema arrayParameter =
                (JsonArraySchema) parameters.properties().get("arrayParameter");
        assertThat(arrayParameter.description()).isEqualTo("An array of strings");
    }

    @Test
    void toolWithObjectParam() throws JsonProcessingException {
        String text =
                // language=json
                """
                [
                  {
                    "name": "operation",
                    "description": "Super operation",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "complexParameter": {
                          "type": "object",
                          "description": "A complex parameter",
                          "properties": {
                            "nestedString": {
                              "type": "string",
                              "description": "A nested string"
                            },
                            "nestedNumber": {
                              "type": "number",
                              "description": "A nested number"
                            }
                          }
                        }
                      },
                      "additionalProperties": false
                    }
                  }
                ]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("operation");
        assertThat(toolSpecification.description()).isEqualTo("Super operation");

        // validate parameters
        JsonObjectSchema parameters = toolSpecification.parameters();
        assertThat(parameters.properties()).hasSize(1);

        JsonObjectSchema complexParameter =
                (JsonObjectSchema) parameters.properties().get("complexParameter");
        assertThat(complexParameter.description()).isEqualTo("A complex parameter");
        assertThat(complexParameter.properties()).hasSize(2);

        JsonStringSchema nestedStringParameter =
                (JsonStringSchema) complexParameter.properties().get("nestedString");
        assertThat(nestedStringParameter.description()).isEqualTo("A nested string");

        JsonNumberSchema nestedNumberParameter =
                (JsonNumberSchema) complexParameter.properties().get("nestedNumber");
        assertThat(nestedNumberParameter.description()).isEqualTo("A nested number");
    }

    @Test
    void toolWithNoParams() throws JsonProcessingException {
        String text =
                // language=json
                """
                [{
                    "name" : "getTinyImage",
                    "description" : "Returns the MCP_TINY_IMAGE",
                    "inputSchema" : {
                        "type" : "object",
                        "properties" : { },
                        "additionalProperties" : false,
                        "$schema" : "http://json-schema.org/draft-07/schema#"
                    }
                }]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("getTinyImage");
        assertThat(toolSpecification.description()).isEqualTo("Returns the MCP_TINY_IMAGE");
        JsonObjectSchema parameters = toolSpecification.parameters();
        assertThat(parameters.properties()).isEmpty();
    }

    @Test
    void arrayWithMultipleAllowedTypes() throws JsonProcessingException {
        String text =
                """
                        [{
                          "name": "query",
                          "description": "Execute a SELECT query",
                          "inputSchema": {
                            "type": "object",
                            "properties": {
                              "sql": {
                                "type": "string",
                                "description": "SQL SELECT query"
                              },
                              "params": {
                                "type": "array",
                                "items": {
                                  "type": [
                                    "string",
                                    "number",
                                    "boolean",
                                    "null",
                                    "integer"
                                  ]
                                },
                                "description": "Query parameters (optional)"
                              }
                            },
                            "required": [
                              "sql"
                            ]
                          }
                        }]
                        """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        JsonObjectSchema parameters = toolSpecification.parameters();
        JsonArraySchema params = (JsonArraySchema) parameters.properties().get("params");
        assertThat(params.description()).isEqualTo("Query parameters (optional)");
        assertThat(params.items()).isInstanceOf(JsonAnyOfSchema.class);
        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) params.items();
        assertThat(anyOf.anyOf().get(0)).isInstanceOf(JsonStringSchema.class);
        assertThat(anyOf.anyOf().get(1)).isInstanceOf(JsonNumberSchema.class);
        assertThat(anyOf.anyOf().get(2)).isInstanceOf(JsonBooleanSchema.class);
        assertThat(anyOf.anyOf().get(3)).isInstanceOf(JsonNullSchema.class);
        assertThat(anyOf.anyOf().get(4)).isInstanceOf(JsonIntegerSchema.class);
    }

    @Test
    void arrayWithAnyOf() throws JsonProcessingException {
        // trimmed version of the tool from @modelcontextprotocol/server-github
        String text =
                // language=json
                """
                [{
                    "name": "create_pull_request_review",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "comments": {
                          "type": "array",
                          "items": {
                            "anyOf": [
                              {
                                "type": "object",
                                "properties": {
                                  "path": {
                                    "type": "string",
                                    "description": "The relative path to the file being commented on"
                                  },
                                  "position": {
                                    "type": "number",
                                    "description": "The position in the diff where you want to add a review comment"
                                  },
                                  "body": {
                                    "type": "string",
                                    "description": "Text of the review comment"
                                  }
                                },
                                "required": [
                                  "path",
                                  "position",
                                  "body"
                                ],
                                "additionalProperties": false
                              },
                              {
                                "type": "object",
                                "properties": {
                                  "path": {
                                    "type": "string",
                                    "description": "The relative path to the file being commented on"
                                  },
                                  "line": {
                                    "type": "number",
                                    "description": "The line number in the file where you want to add a review comment"
                                  },
                                  "body": {
                                    "type": "string",
                                    "description": "Text of the review comment"
                                  }
                                },
                                "required": [
                                  "path",
                                  "line",
                                  "body"
                                ],
                                "additionalProperties": false
                              }
                            ]
                          },
                          "description": "Comments to post as part of the review (specify either position or line, not both)"
                        }
                      },
                      "additionalProperties": false,
                      "$schema": "http://json-schema.org/draft-07/schema#"
                    }
                }]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);

        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        JsonObjectSchema parameters = toolSpecification.parameters();
        JsonArraySchema comments = (JsonArraySchema) parameters.properties().get("comments");
        assertThat(comments.items()).isInstanceOf(JsonAnyOfSchema.class);
        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) comments.items();
        assertThat(anyOf.anyOf()).hasSize(2);

        JsonSchemaElement option1 = anyOf.anyOf().get(0);
        assertThat(option1).isInstanceOf(JsonObjectSchema.class);
        assertThat(((JsonObjectSchema) option1).properties()).containsOnlyKeys("path", "position", "body");

        JsonSchemaElement option2 = anyOf.anyOf().get(1);
        assertThat(option2).isInstanceOf(JsonObjectSchema.class);
        assertThat(((JsonObjectSchema) option2).properties()).containsOnlyKeys("path", "line", "body");
    }

    @Test
    void nullTypeName() throws JsonProcessingException {
        // the 'value' parameter has an empty definition, so it can be anything
        String text =
                """
                [{
                   "name": "set_config_value",
                   "description": "Set a specific configuration value by key",
                   "inputSchema": {
                     "type": "object",
                     "properties": {
                       "key": {
                         "type": "string"
                       },
                       "value": {}
                     },
                     "required": [
                       "key"
                     ],
                     "additionalProperties": false,
                     "$schema": "http://json-schema.org/draft-07/schema#"
                   }
                 }]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);
        assertThat(toolSpecifications.get(0).parameters().properties().get("value"))
                .isInstanceOf(JsonObjectSchema.class);
    }

    @Test
    void nullType() throws JsonProcessingException {
        // the 'type' parameter is "null" and so most not be present
        // trimmed version from Atalassian's MCP server
        String text =
                """
                [{
                  "name": "createCompassCustomFieldDefinition",
                  "description": "Create a new Compass custom field definition",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "cloudId": {
                        "type": "string",
                        "description": "Unique identifier for an Atlassian Cloud instance in the form of a UUID. Can also be a site URL. If not working, use the 'getAccessibleAtlassianResources' tool to find accessible Cloud IDs."
                      },
                      "fieldSelections": {
                        "anyOf": [
                          {
                            "type": "object",
                            "additionalProperties": {
                              "type": "null"
                            },
                            "description": "An list of options for the custom field definition, expressed as maps. The keys should be the options, and the values should all be null. Only used for SingleSelect and MultiSelect custom field definitions."
                          },
                          {
                            "type": "null"
                          }
                        ],
                        "description": "An list of options for the custom field definition, expressed as maps. The keys should be the options, and the values should all be null. Only used for SingleSelect and MultiSelect custom field definitions."
                      }
                    },
                    "required": [
                      "cloudId"
                    ],
                    "additionalProperties": false,
                    "$schema": "http://json-schema.org/draft-07/schema#"
                  }
                }]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);
        assertThat(toolSpecifications.get(0).parameters().properties().get("fieldSelections"))
                .isInstanceOf(JsonAnyOfSchema.class);
        JsonAnyOfSchema jsAny = (JsonAnyOfSchema)
                toolSpecifications.get(0).parameters().properties().get("fieldSelections");
        assertThat(jsAny.anyOf()).hasSize(2);
        assertThat(jsAny.anyOf().get(0)).isInstanceOf(JsonObjectSchema.class);
        assertThat(jsAny.anyOf().get(1)).isInstanceOf(JsonNullSchema.class);
    }

    @Test
    void arrayWithoutSpecifiedType() throws JsonProcessingException {
        String text =
                // language=json
                """
                [{
                    "name": "something",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "arrayparam": {
                          "type": "array",
                          "description": "An array of whatever you like"
                        }
                      },
                      "additionalProperties": false,
                      "$schema": "http://json-schema.org/draft-07/schema#"
                    }
                }]
                """;
        ArrayNode json = OBJECT_MAPPER.readValue(text, ArrayNode.class);
        List<ToolSpecification> toolSpecifications = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json);

        assertThat(toolSpecifications).hasSize(1);
        JsonSchemaElement parameter =
                toolSpecifications.get(0).parameters().properties().get("arrayparam");
        assertThat(parameter).isInstanceOf(JsonArraySchema.class);
        assertThat(((JsonArraySchema) parameter).items()).isNull();
    }
}
