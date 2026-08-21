package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.ibm.watsonx.ai.chat.ChatResponse.ResultChoice;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatUsage;
import com.ibm.watsonx.ai.chat.model.ResultMessage;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.ToolMessage;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Cache;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.Router;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.output.FinishReason;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class ConverterTest {

    @Test
    void testToSystemMessage() {

        var chatMessage = Converter.toChatMessage(SystemMessage.from("test"));

        if (!(chatMessage instanceof com.ibm.watsonx.ai.chat.model.SystemMessage))
            fail("chatMessage is not an instance of SystemMessage");

        var systemMessage = (com.ibm.watsonx.ai.chat.model.SystemMessage) chatMessage;
        assertEquals(com.ibm.watsonx.ai.chat.model.SystemMessage.ROLE, systemMessage.role());
        assertEquals("test", systemMessage.content());
    }

    @Test
    void testToAssistantMessage() {

        var toolExecutionRequest = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"name\":\"Klaus\",\"address\":null}")
                .build();

        var aiMessage = AiMessage.builder()
                .toolExecutionRequests(List.of(toolExecutionRequest))
                .build();

        var chatMessage = Converter.toChatMessage(aiMessage);

        if (!(chatMessage instanceof AssistantMessage)) fail("chatMessage is not an instance of AssistantMessage");

        var assistantMessage = (AssistantMessage) chatMessage;
        assertEquals(AssistantMessage.ROLE, assistantMessage.role());
        assertNull(assistantMessage.name());
        assertNull(assistantMessage.content());
        assertEquals(1, assistantMessage.toolCalls().size());
        assertEquals(
                toolExecutionRequest,
                Converter.toToolExecutionRequest(assistantMessage.toolCalls().get(0)));

        aiMessage = AiMessage.builder().text("text").build();

        chatMessage = Converter.toChatMessage(aiMessage);
        assistantMessage = (AssistantMessage) chatMessage;

        assertEquals(AssistantMessage.ROLE, assistantMessage.role());
        assertNull(assistantMessage.name());
        assertEquals("text", assistantMessage.content());
        assertNull(assistantMessage.toolCalls());

        var audioContent = UserMessage.builder()
                .contents(List.of(AudioContent.from("test")))
                .build();

        var pdfContent = UserMessage.builder()
                .contents(List.of(PdfFileContent.from("test")))
                .build();

        var videoContent = UserMessage.builder()
                .contents(List.of(VideoContent.from("test")))
                .build();

        assertThrows(RuntimeException.class, () -> Converter.toChatMessage(audioContent));
        assertThrows(RuntimeException.class, () -> Converter.toChatMessage(pdfContent));
        assertThrows(RuntimeException.class, () -> Converter.toChatMessage(videoContent));
    }

    @Test
    void testToToolMessage() {

        var toolExecutionResultMessage = ToolExecutionResultMessage.from("id", "toolName", "result");
        var chatMessage = Converter.toChatMessage(toolExecutionResultMessage);

        if (!(chatMessage instanceof ToolMessage)) fail("chatMessage is not an instance of ToolMessage");

        var toolMessage = (ToolMessage) chatMessage;
        assertEquals(ToolMessage.ROLE, toolMessage.role());
        assertEquals("id", toolMessage.toolCallId());
        assertEquals("result", toolMessage.content());
    }

    @Test
    void testToToolMessageWithNonTextContent() {

        var nonTextResult = ToolExecutionResultMessage.builder()
                .id("id")
                .toolName("toolName")
                .contents(ImageContent.from("data", "image/png"))
                .build();

        assertThrows(UnsupportedFeatureException.class, () -> Converter.toChatMessage(nonTextResult));
    }

    @Test
    void testToUserMessage() {

        var chatMessage = Converter.toChatMessage(UserMessage.builder()
                .name("name")
                .addContent(TextContent.from("text"))
                .addContent(ImageContent.from("data", "image/png", DetailLevel.HIGH))
                .addContent(ImageContent.from("data", "image/jpg", DetailLevel.AUTO))
                .addContent(ImageContent.from("data", "image/gif", DetailLevel.LOW))
                .build());

        if (!(chatMessage instanceof com.ibm.watsonx.ai.chat.model.UserMessage))
            fail("chatMessage is not an instance of UserMessage");

        var userMessage = (com.ibm.watsonx.ai.chat.model.UserMessage) chatMessage;
        assertEquals(com.ibm.watsonx.ai.chat.model.UserMessage.ROLE, userMessage.role());
        assertEquals("name", userMessage.name());
        assertEquals(4, userMessage.content().size());

        var textContent = (com.ibm.watsonx.ai.chat.model.TextContent)
                userMessage.content().get(0);
        assertEquals(com.ibm.watsonx.ai.chat.model.TextContent.TYPE, textContent.type());
        assertEquals("text", textContent.text());

        var imageContent = (com.ibm.watsonx.ai.chat.model.ImageContent)
                userMessage.content().get(1);
        assertEquals(com.ibm.watsonx.ai.chat.model.ImageContent.TYPE, imageContent.type());
        assertEquals("data:image/png;base64,data", imageContent.imageUrl().url());
        assertEquals("high", imageContent.imageUrl().detail());

        imageContent = (com.ibm.watsonx.ai.chat.model.ImageContent)
                userMessage.content().get(2);
        assertEquals(com.ibm.watsonx.ai.chat.model.ImageContent.TYPE, imageContent.type());
        assertEquals("data:image/jpg;base64,data", imageContent.imageUrl().url());
        assertEquals("auto", imageContent.imageUrl().detail());

        imageContent = (com.ibm.watsonx.ai.chat.model.ImageContent)
                userMessage.content().get(3);
        assertEquals(com.ibm.watsonx.ai.chat.model.ImageContent.TYPE, imageContent.type());
        assertEquals("data:image/gif;base64,data", imageContent.imageUrl().url());
        assertEquals("low", imageContent.imageUrl().detail());

        assertThrows(
                UnsupportedFeatureException.class,
                () -> Converter.toChatMessage(UserMessage.builder()
                        .name("name")
                        .addContent(ImageContent.from(URI.create("http://test.com")))
                        .build()));
    }

    @Test
    void testToCustomMessage() {
        assertThrows(UnsupportedOperationException.class, () -> Converter.toChatMessage(CustomMessage.from(Map.of())));
    }

    @Test
    void testToTool() {

        var toolSpecification = ToolSpecification.builder()
                .description("description")
                .name("name")
                .parameters(JsonObjectSchema.builder()
                        .addBooleanProperty("boolean", "boolean description")
                        .addEnumProperty("enum", List.of("enum1", "enum2"), "enum description")
                        .addIntegerProperty("integer", "integer description")
                        .addNumberProperty("number", "number description")
                        .addStringProperty("string", "string description")
                        .addProperty(
                                "object",
                                JsonObjectSchema.builder()
                                        .addBooleanProperty("boolean")
                                        .build())
                        .build())
                .build();

        var tool = Converter.toTool(toolSpecification);
        assertEquals("description", tool.function().description());
        assertEquals("name", tool.function().name());
        assertEquals(
                JsonSchemaElementUtils.toMap(toolSpecification.parameters()),
                tool.function().parameters());

        tool = Converter.toTool(ToolSpecification.builder()
                .description("description")
                .name("name")
                .build());

        assertEquals("description", tool.function().description());
        assertEquals("name", tool.function().name());
        assertNull(tool.function().parameters());
    }

    @Test
    void testToToolExecutionRequest() {

        var toolCall = ToolCall.of("id", "name", "{\"name\":\"Klaus\",\"address\":null}");
        var toolExecutionRequest = Converter.toToolExecutionRequest(toolCall);
        assertEquals("id", toolExecutionRequest.id());
        assertEquals("name", toolExecutionRequest.name());
        assertEquals("{\"name\":\"Klaus\",\"address\":null}", toolExecutionRequest.arguments());
    }

    @Test
    void testToFinishReason() {

        assertEquals(FinishReason.LENGTH, Converter.toFinishReason("length"));
        assertEquals(FinishReason.STOP, Converter.toFinishReason("stop"));
        assertEquals(FinishReason.TOOL_EXECUTION, Converter.toFinishReason("tool_calls"));
        assertEquals(FinishReason.OTHER, Converter.toFinishReason("time_limit"));
        assertEquals(FinishReason.OTHER, Converter.toFinishReason("cancelled"));
        assertEquals(FinishReason.OTHER, Converter.toFinishReason("error"));
        assertEquals(FinishReason.OTHER, Converter.toFinishReason(null));
        assertThrows(IllegalArgumentException.class, () -> Converter.toFinishReason("notExiust"));
    }

    @Test
    void testToCompleteToolCall() {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"name\":\"Klaus\",\"address\":null}")
                .build();

        ToolCall toolCall = ToolCall.of(10, "id", "name", "{\"name\":\"Klaus\",\"address\":null}");
        assertEquals(new CompleteToolCall(10, toolExecutionRequest), Converter.toCompleteToolCall(toolCall));
    }

    @Test
    void testToPartialToolCall() {

        var EXPECTED = PartialToolCall.builder()
                .id("id")
                .index(10)
                .name("name")
                .partialArguments("{\"name\":\"Klaus\"")
                .build();

        var toConvert = new com.ibm.watsonx.ai.chat.model.PartialToolCall(
                "completion-id", 0, 10, "id", "name", "{\"name\":\"Klaus\"");
        assertEquals(EXPECTED, Converter.toPartialToolCall(toConvert));
    }

    @Test
    void testToChatParameters_withAllFieldsSet() {
        var parameters = WatsonxChatRequestParameters.builder()
                .frequencyPenalty(0.1)
                .maxOutputTokens(0)
                .modelName("modelName")
                .presencePenalty(0.2)
                .stopSequences("[")
                .temperature(0.3)
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(ResponseFormat.TEXT)
                .timeout(Duration.ofMillis(30))
                .topK(1)
                .topP(0.4)
                .projectId("projectId")
                .logitBias(Map.of("test", 10))
                .logprobs(true)
                .seed(5)
                .spaceId("spaceId")
                .toolChoiceName("toolChoiceName")
                .toolSpecifications(ToolSpecification.builder().name("test").build())
                .topLogprobs(10)
                .guidedChoice("a", "b")
                .guidedGrammar("guidedGrammar")
                .guidedRegex("guidedRegex")
                .lengthPenalty(1.1)
                .repetitionPenalty(1.2)
                .build();

        var p = Converter.toChatParameters(parameters, false);
        assertEquals(0.1, p.frequencyPenalty());
        assertEquals(0, p.maxCompletionTokens());
        assertEquals("modelName", p.modelId());
        assertEquals(0.2, p.presencePenalty());
        assertEquals(List.of("["), p.stop());
        assertEquals(0.3, p.temperature());
        assertEquals("auto", p.toolChoiceOption());
        assertEquals(0.4, p.topP());
        assertEquals(30, p.timeLimit());
        assertEquals("projectId", p.projectId());
        assertEquals(Map.of("test", 10), p.logitBias());
        assertEquals(true, p.logprobs());
        assertEquals(5, p.seed());
        assertEquals("spaceId", p.spaceId());
        assertEquals(10, p.topLogprobs());
        assertEquals(Set.of("a", "b"), p.guidedChoice());
        assertEquals("guidedGrammar", p.guidedGrammar());
        assertEquals("guidedRegex", p.guidedRegex());
        assertEquals(1.1, p.lengthPenalty());
        assertEquals(1.2, p.repetitionPenalty());
        assertNull(p.responseFormat());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testToChatParameters_withToolChoiceRequiredAndMatchingTool() {
        var parameters = WatsonxChatRequestParameters.builder()
                .toolChoice(ToolChoice.REQUIRED)
                .toolSpecifications(
                        ToolSpecification.builder().name("toolChoiceName").build())
                .build();

        var p = Converter.toChatParameters(parameters, false);
        assertEquals("required", p.toolChoiceOption());

        parameters = WatsonxChatRequestParameters.builder()
                .toolChoice(ToolChoice.REQUIRED)
                .toolChoiceName("toolChoiceName")
                .toolSpecifications(
                        ToolSpecification.builder().name("toolChoiceName").build())
                .build();

        p = Converter.toChatParameters(parameters, false);
        assertNull(p.toolChoiceOption());
        assertEquals("function", p.toolChoice().get("type"));
        assertEquals("toolChoiceName", ((Map) p.toolChoice().get("function")).get("name"));
    }

    @Test
    void testToChatParameters_withToolChoiceNone() {
        var parameters = WatsonxChatRequestParameters.builder()
                .toolChoice(ToolChoice.NONE)
                .build();

        var p = Converter.toChatParameters(parameters, false);
        assertEquals("none", p.toolChoiceOption());
    }

    @Test
    void testToChatParameters_withoutStopSequences() {
        assertNull(Converter.toChatParameters(
                        WatsonxChatRequestParameters.builder().build(), false)
                .stop());
        assertNull(Converter.toChatParameters(
                        WatsonxChatRequestParameters.builder()
                                .stopSequences(List.of())
                                .build(),
                        false)
                .stop());
    }

    @Test
    void testToChatParameters_withInvalidToolChoice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toChatParameters(
                        WatsonxChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .build(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toChatParameters(
                        WatsonxChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("toolChoiceName")
                                .build(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toChatParameters(
                        WatsonxChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("toolChoiceName")
                                .toolSpecifications(List.of())
                                .build(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toChatParameters(
                        WatsonxChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("toolChoiceName")
                                .toolSpecifications(ToolSpecification.builder()
                                        .name("notMatch")
                                        .build())
                                .build(),
                        false));
    }

    @Test
    void testToChatParameters_withResponseFormat() throws Exception {
        var parameters = WatsonxChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        var p = Converter.toChatParameters(parameters, false);
        assertEquals("json_object", p.responseFormat());

        parameters = WatsonxChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonObjectSchema.builder()
                                .addBooleanProperty("test")
                                .build())
                        .build())
                .build();

        p = Converter.toChatParameters(parameters, false);
        assertEquals("json_schema", p.responseFormat());
        assertEquals("test", p.jsonSchema().name());
        assertEquals(false, p.jsonSchema().strict());
        JSONAssert.assertEquals("""
                                {
                                    "type" : "object",
                                    "properties" : {
                                        "test" : {
                                          "type" : boolean
                                        }
                                    },
                                    required : [ ]
                                }""", Json.toJson(p.jsonSchema().schema()), true);
    }

    @Test
    void testToChatParameters_withStrictJsonSchema() throws Exception {
        var parameters = WatsonxChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("content")
                                .addBooleanProperty("flag")
                                .required("content")
                                .build())
                        .build())
                .build();

        var p = Converter.toChatParameters(parameters, true);
        assertEquals("json_schema", p.responseFormat());
        assertEquals("test", p.jsonSchema().name());
        assertEquals(true, p.jsonSchema().strict());
        JSONAssert.assertEquals("""
                {
                    "type" : "object",
                    "properties" : {
                        "content" : {
                            "type" : "string"
                        },
                        "flag" : {
                            "type" : [ "boolean", "null" ]
                        }
                    },
                    "required" : [ "content", "flag" ],
                    "additionalProperties" : false
                }""", Json.toJson(p.jsonSchema().schema()), true);
    }

    @Test
    void testToChatParameters_withRawJsonSchema() throws Exception {
        var rawSchema = """
                {
                    "type" : "object",
                    "properties" : {
                        "content" : {
                            "type" : "string"
                        }
                    },
                    "required" : [ "content" ],
                    "additionalProperties" : true
                }""";

        var parameters = WatsonxChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonRawSchema.from(rawSchema))
                        .build())
                .build();

        for (boolean strict : List.of(true, false)) {
            var p = Converter.toChatParameters(parameters, strict);
            assertEquals("json_schema", p.responseFormat());
            assertEquals(strict, p.jsonSchema().strict());
            JSONAssert.assertEquals(rawSchema, Json.toJson(p.jsonSchema().schema()), true);
        }
    }

    @Test
    void testToChatParameters_withInvalidJsonSchemaRootElement() {
        var parameters = WatsonxChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonStringSchema.builder().build())
                        .build())
                .build();

        var exception =
                assertThrows(IllegalArgumentException.class, () -> Converter.toChatParameters(parameters, false));

        assertEquals(
                "The root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                        + JsonStringSchema.class,
                exception.getMessage());
    }

    @Test
    void testToModelGatewayParameters_withAllFieldsSet() {
        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .frequencyPenalty(0.1)
                .maxOutputTokens(0)
                .modelName("modelName")
                .presencePenalty(0.2)
                .stopSequences("[")
                .temperature(0.3)
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(ResponseFormat.TEXT)
                .timeout(Duration.ofMillis(30))
                .topP(0.4)
                .logitBias(Map.of("test", 10))
                .logprobs(true)
                .seed(5)
                .toolChoiceName("toolChoiceName")
                .toolSpecifications(ToolSpecification.builder().name("test").build())
                .topLogprobs(10)
                .serviceTier(ServiceTier.AUTO)
                .reasoningEffort(ReasoningEffort.LOW)
                .router(new Router(new Cache(true, null, null)))
                .modalities(List.of("text"))
                .store(true)
                .parallelToolCalls(true)
                .user("user")
                .metadata(Map.of("k", "v"))
                .build();

        var p = Converter.toModelGatewayParameters(parameters, false);
        assertEquals(0.1, p.frequencyPenalty());
        assertEquals(0, p.maxCompletionTokens());
        assertEquals("modelName", p.modelId());
        assertEquals(0.2, p.presencePenalty());
        assertEquals(List.of("["), p.stop());
        assertEquals(0.3, p.temperature());
        assertEquals("auto", p.toolChoiceOption());
        assertEquals(0.4, p.topP());
        assertEquals(30, p.timeLimit());
        assertEquals(Map.of("test", 10), p.logitBias());
        assertEquals(true, p.logprobs());
        assertEquals(5, p.seed());
        assertEquals(10, p.topLogprobs());
        assertEquals("auto", p.serviceTier());
        assertEquals("low", p.reasoningEffort());
        assertEquals(new Router(new Cache(true, null, null)), p.router());
        assertEquals(List.of("text"), p.modalities());
        assertEquals(true, p.store());
        assertEquals(true, p.parallelToolCalls());
        assertEquals("user", p.user());
        assertEquals(Map.of("k", "v"), p.metadata());
        assertNull(p.responseFormat());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testToModelGatewayParameters_withToolChoiceRequiredAndMatchingTool() {
        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .toolChoice(ToolChoice.REQUIRED)
                .toolSpecifications(
                        ToolSpecification.builder().name("toolChoiceName").build())
                .build();

        var p = Converter.toModelGatewayParameters(parameters, false);
        assertEquals("required", p.toolChoiceOption());

        parameters = WatsonxGatewayChatRequestParameters.builder()
                .toolChoice(ToolChoice.REQUIRED)
                .toolChoiceName("toolChoiceName")
                .toolSpecifications(
                        ToolSpecification.builder().name("toolChoiceName").build())
                .build();

        p = Converter.toModelGatewayParameters(parameters, false);
        assertNull(p.toolChoiceOption());
        assertEquals("function", p.toolChoice().get("type"));
        assertEquals("toolChoiceName", ((Map) p.toolChoice().get("function")).get("name"));
    }

    @Test
    void testToModelGatewayParameters_withToolChoiceNone() {
        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .toolChoice(ToolChoice.NONE)
                .build();

        var p = Converter.toModelGatewayParameters(parameters, false);
        assertEquals("none", p.toolChoiceOption());
    }

    @Test
    void testToModelGatewayParameters_withoutStopSequences() {
        // The Model Gateway rejects an empty "stop" array with "Field validation for 'Sequences' failed on the
        // 'min' tag", so it must not be sent.
        assertNull(Converter.toModelGatewayParameters(
                        WatsonxGatewayChatRequestParameters.builder().build(), false)
                .stop());
        assertNull(Converter.toModelGatewayParameters(
                        WatsonxGatewayChatRequestParameters.builder()
                                .stopSequences(List.of())
                                .build(),
                        false)
                .stop());
    }

    @Test
    void testToModelGatewayParameters_withInvalidToolChoice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toModelGatewayParameters(
                        WatsonxGatewayChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .build(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toModelGatewayParameters(
                        WatsonxGatewayChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("toolChoiceName")
                                .build(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toModelGatewayParameters(
                        WatsonxGatewayChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("toolChoiceName")
                                .toolSpecifications(List.of())
                                .build(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> Converter.toModelGatewayParameters(
                        WatsonxGatewayChatRequestParameters.builder()
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("toolChoiceName")
                                .toolSpecifications(ToolSpecification.builder()
                                        .name("notMatch")
                                        .build())
                                .build(),
                        false));
    }

    @Test
    void testToModelGatewayParameters_withResponseFormat() throws Exception {
        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        var p = Converter.toModelGatewayParameters(parameters, false);
        assertEquals("json_object", p.responseFormat());

        parameters = WatsonxGatewayChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonObjectSchema.builder()
                                .addBooleanProperty("test")
                                .build())
                        .build())
                .build();

        p = Converter.toModelGatewayParameters(parameters, false);
        assertEquals("json_schema", p.responseFormat());
        assertEquals("test", p.jsonSchema().name());
        assertEquals(false, p.jsonSchema().strict());
        JSONAssert.assertEquals("""
                                {
                                    "type" : "object",
                                    "properties" : {
                                        "test" : {
                                          "type" : boolean
                                        }
                                    },
                                    required : [ ]
                                }""", Json.toJson(p.jsonSchema().schema()), true);
    }

    @Test
    void testToModelGatewayParameters_withStrictJsonSchema() throws Exception {
        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("content")
                                .addBooleanProperty("flag")
                                .required("content")
                                .build())
                        .build())
                .build();

        var p = Converter.toModelGatewayParameters(parameters, true);
        assertEquals("json_schema", p.responseFormat());
        assertEquals("test", p.jsonSchema().name());
        assertEquals(true, p.jsonSchema().strict());
        JSONAssert.assertEquals("""
                {
                    "type" : "object",
                    "properties" : {
                        "content" : {
                            "type" : "string"
                        },
                        "flag" : {
                            "type" : [ "boolean", "null" ]
                        }
                    },
                    "required" : [ "content", "flag" ],
                    "additionalProperties" : false
                }""", Json.toJson(p.jsonSchema().schema()), true);
    }

    @Test
    void testToModelGatewayParameters_withInvalidJsonSchemaRootElement() {
        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .responseFormat(JsonSchema.builder()
                        .name("test")
                        .rootElement(JsonStringSchema.builder().build())
                        .build())
                .build();

        var exception = assertThrows(
                IllegalArgumentException.class, () -> Converter.toModelGatewayParameters(parameters, false));

        assertEquals(
                "The root element of the JSON Schema must be either a JsonObjectSchema or a JsonRawSchema, but it was: "
                        + JsonStringSchema.class,
                exception.getMessage());
    }

    @Test
    void testToChatResponseWithoutUsage() {

        var withoutUsage = TextChatResponse.builder()
                .id("id")
                .modelId("modelId")
                .createdAt("createdAt")
                .created(1L)
                .choices(List.of(new ResultChoice(
                        0, new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null), "stop")))
                .build();

        assertNull(Converter.toChatResponse(withoutUsage).tokenUsage());
    }

    @Test
    void testToChatResponseWithoutGatewayMetadata() {

        var plainResponse = TextChatResponse.builder()
                .id("id")
                .modelId("modelId")
                .modelVersion("modelVersion")
                .createdAt("createdAt")
                .created(1L)
                .usage(new ChatUsage(10, 10, 20))
                .choices(List.of(new ResultChoice(
                        0, new ResultMessage(AssistantMessage.ROLE, "Hello", null, null, null), "stop")))
                .build();

        var metadata = assertInstanceOf(
                WatsonxChatResponseMetadata.class,
                Converter.toChatResponse(plainResponse).metadata());

        assertNull(metadata.getServiceTier());
        assertNull(metadata.getSystemFingerprint());
        assertNull(metadata.getCached());
    }
}
