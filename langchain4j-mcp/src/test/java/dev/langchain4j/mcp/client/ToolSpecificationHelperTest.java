package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ToolSpecificationHelperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void toolWithSimpleParams() throws JsonProcessingException {
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
        assertThat(toolSpecifications.size()).isEqualTo(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("operation");
        assertThat(toolSpecification.description()).isEqualTo("Super operation");

        // validate parameters
        JsonObjectSchema parameters = toolSpecification.parameters();
        assertThat(parameters.properties().size()).isEqualTo(6);
        assertThat(parameters.required().size()).isEqualTo(1);
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
    public void toolWithObjectParam() throws JsonProcessingException {
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
        assertThat(toolSpecifications.size()).isEqualTo(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("operation");
        assertThat(toolSpecification.description()).isEqualTo("Super operation");

        // validate parameters
        JsonObjectSchema parameters = toolSpecification.parameters();
        assertThat(parameters.properties().size()).isEqualTo(1);

        JsonObjectSchema complexParameter =
                (JsonObjectSchema) parameters.properties().get("complexParameter");
        assertThat(complexParameter.description()).isEqualTo("A complex parameter");
        assertThat(complexParameter.properties().size()).isEqualTo(2);

        JsonStringSchema nestedStringParameter =
                (JsonStringSchema) complexParameter.properties().get("nestedString");
        assertThat(nestedStringParameter.description()).isEqualTo("A nested string");

        JsonNumberSchema nestedNumberParameter =
                (JsonNumberSchema) complexParameter.properties().get("nestedNumber");
        assertThat(nestedNumberParameter.description()).isEqualTo("A nested number");
    }

    @Test
    public void toolWithNoParams() throws JsonProcessingException {
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
        assertThat(toolSpecifications.size()).isEqualTo(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("getTinyImage");
        assertThat(toolSpecification.description()).isEqualTo("Returns the MCP_TINY_IMAGE");
        JsonObjectSchema parameters = toolSpecification.parameters();
        assertThat(parameters.properties().size()).isEqualTo(0);
    }
}
