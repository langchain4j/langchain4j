package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.model.vertexai.gemini.FunctionCallHelper.unwrapProtoValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.api.FunctionCall;
import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.Type;
import com.google.protobuf.ByteString;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.Value;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionCallHelperTest {

    @Test
    void should_unwrap_proto_values() {
        // check basic values
        assertThat(unwrapProtoValue(Value.newBuilder().setStringValue("hello").build()))
                .isEqualTo("hello");
        assertThat(unwrapProtoValue(Value.newBuilder().setBoolValue(false).build()))
                .isEqualTo(false);
        assertThat(unwrapProtoValue(Value.newBuilder().setNumberValue(1.23).build()))
                .isEqualTo(1.23);
        assertThat(unwrapProtoValue(
                        Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()))
                .isNull();

        // check list unwrapping
        ListValue listValue = ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("hello"))
                .addValues(Value.newBuilder().setBoolValue(true))
                .addValues(Value.newBuilder().setNumberValue(3.14))
                .build();
        assertThat(unwrapProtoValue(Value.newBuilder().setListValue(listValue).build()))
                .isEqualTo(Arrays.asList("hello", true, 3.14));

        // check struct unwrapping
        Struct struct = Struct.newBuilder()
                .putFields(
                        "name", Value.newBuilder().setStringValue("Guillaume").build())
                .putFields("numberOfKids", Value.newBuilder().setNumberValue(2).build())
                .putFields(
                        "kids",
                        Value.newBuilder()
                                .setListValue(ListValue.newBuilder()
                                        .addValues(Value.newBuilder()
                                                .setStringValue("Marion")
                                                .build())
                                        .addValues(Value.newBuilder()
                                                .setStringValue("Érine")
                                                .build())
                                        .build())
                                .build())
                .putFields("flag", Value.newBuilder().setBoolValue(false).build())
                .build();
        HashMap<Object, Object> map = new HashMap<>();
        map.put("name", "Guillaume");
        map.put("numberOfKids", 2.0);
        map.put("kids", Arrays.asList("Marion", "Érine"));
        map.put("flag", false);
        assertThat(unwrapProtoValue(Value.newBuilder().setStructValue(struct).build()))
                .isEqualTo(map);
    }

    @Test
    void should_convert_tool_specs() {
        // given
        ToolSpecification toolSpec = ToolSpecification.builder()
                .description("Give the weather forecast for a location")
                .name("getWeatherForecast")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location", "the location to get the weather forecast for")
                        .addIntegerProperty("days", "the number of days in the forecast")
                        .required("location")
                        .build())
                .build();

        // when
        Tool tool = FunctionCallHelper.convertToolSpecifications(Collections.singletonList(toolSpec));

        // then
        assertThat(tool.getFunctionDeclarationsCount()).isEqualTo(1);

        FunctionDeclaration funDecl = tool.getFunctionDeclarations(0);
        assertThat(funDecl.getDescription()).isEqualTo("Give the weather forecast for a location");
        assertThat(funDecl.getName()).isEqualTo("getWeatherForecast");

        Schema parameters = funDecl.getParameters();
        assertThat(parameters.getPropertiesCount()).isEqualTo(2);
        assertThat(parameters.getPropertiesMap().get("location").getType()).isEqualTo(Type.STRING);
        assertThat(parameters.getPropertiesMap().get("location").getDescription())
                .isEqualTo("the location to get the weather forecast for");
        assertThat(parameters.getRequiredCount()).isEqualTo(1);
        assertThat(parameters.getRequired(0)).isEqualTo("location");
        assertThat(parameters.getPropertiesMap().get("days").getType()).isEqualTo(Type.INTEGER);
    }

    @Test
    void should_convert_function_calls_to_tool_execution_requests_and_back() {
        // given
        FunctionCall functionCall = FunctionCall.newBuilder()
                .setName("getWeatherForecast")
                .setArgs(Struct.newBuilder()
                        .putFields(
                                "location",
                                Value.newBuilder().setStringValue("Paris").build())
                        .build())
                .build();

        // when
        List<ToolExecutionRequest> toolExecutionRequest =
                FunctionCallHelper.fromFunctionCalls(Collections.singletonList(functionCall));
        FunctionCall sameFunctionCall = FunctionCallHelper.fromToolExecutionRequest(toolExecutionRequest.get(0));

        // then
        assertThat(functionCall).isEqualTo(sameFunctionCall);

        // given
        ToolExecutionRequest newExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeatherForecast")
                .id("0")
                .arguments("{\"location\":\"Paris\"}")
                .build();

        // when
        FunctionCall newFunctionCall = FunctionCallHelper.fromToolExecutionRequest(newExecutionRequest);
        ToolExecutionRequest sameExecutionRequest = FunctionCallHelper.fromFunctionCalls(
                        Collections.singletonList(newFunctionCall))
                .get(0);

        // then
        assertThat(newExecutionRequest).isEqualTo(sameExecutionRequest);
    }

    @Test
    void should_convert_function_calls_to_tool_execution_requests_and_back_without_args() {
        // given
        FunctionCall functionCall = FunctionCall.newBuilder()
                .setName("getDataSources")
                .setArgs(Struct.newBuilder())
                .build();

        // when
        List<ToolExecutionRequest> toolExecutionRequest =
                FunctionCallHelper.fromFunctionCalls(Collections.singletonList(functionCall));
        FunctionCall sameFunctionCall = FunctionCallHelper.fromToolExecutionRequest(toolExecutionRequest.get(0));

        // then
        assertThat(functionCall).isEqualTo(sameFunctionCall);

        // given
        ToolExecutionRequest newExecutionRequest = ToolExecutionRequest.builder()
                .id("0")
                .name("getDataSources")
                .arguments("{}")
                .build();

        // when
        FunctionCall newFunctionCall = FunctionCallHelper.fromToolExecutionRequest(newExecutionRequest);
        ToolExecutionRequest sameExecutionRequest = FunctionCallHelper.fromFunctionCalls(
                        Collections.singletonList(newFunctionCall))
                .get(0);

        // then
        assertThat(newExecutionRequest).isEqualTo(sameExecutionRequest);
    }

    @Test
    void should_round_trip_function_call_part_thought_signature() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder()
                        .setName("getWeatherForecast")
                        .setArgs(Struct.newBuilder()
                                .putFields(
                                        "location",
                                        Value.newBuilder()
                                                .setStringValue("Paris")
                                                .build())
                                .build())
                        .build())
                .mergeUnknownFields(unknownFields("thought-signature"))
                .build();

        // when
        AiMessage aiMessage =
                FunctionCallHelper.fromFunctionCallParts(Collections.singletonList(functionCallPart), true);
        Part sameFunctionCallPart =
                FunctionCallHelper.toFunctionCallParts(aiMessage, true).get(0);

        // then
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields()).isEqualTo(functionCallPart.getUnknownFields());
    }

    @Test
    void should_not_round_trip_non_thought_signature_unknown_fields() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder()
                        .setName("getWeatherForecast")
                        .setArgs(Struct.newBuilder())
                        .build())
                .mergeUnknownFields(unknownFields("thought-signature"))
                .mergeUnknownFields(unknownFields(99, "unrelated"))
                .build();

        // when
        AiMessage aiMessage =
                FunctionCallHelper.fromFunctionCallParts(Collections.singletonList(functionCallPart), true);
        Part sameFunctionCallPart =
                FunctionCallHelper.toFunctionCallParts(aiMessage, true).get(0);

        // then
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields().asMap()).containsOnlyKeys(11);
        assertThat(sameFunctionCallPart.getUnknownFields().asMap().get(11))
                .isEqualTo(functionCallPart.getUnknownFields().asMap().get(11));
    }

    @Test
    void should_not_send_non_thought_signature_unknown_fields_from_attributes() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder()
                        .setName("getWeatherForecast")
                        .setArgs(Struct.newBuilder())
                        .build())
                .mergeUnknownFields(unknownFields("thought-signature"))
                .build();

        AiMessage aiMessage =
                FunctionCallHelper.fromFunctionCallParts(Collections.singletonList(functionCallPart), true);
        String attributeKey = aiMessage.attributes().keySet().iterator().next();
        String encodedUnknownFields = Base64.getEncoder()
                .encodeToString(UnknownFieldSet.newBuilder()
                        .mergeFrom(unknownFields("thought-signature"))
                        .mergeFrom(unknownFields(99, "unrelated"))
                        .build()
                        .toByteArray());

        HashMap<String, Object> attributes = new HashMap<>(aiMessage.attributes());
        attributes.put(attributeKey, Collections.singletonList(encodedUnknownFields));
        AiMessage aiMessageWithExtraUnknownField = AiMessage.builder()
                .toolExecutionRequests(aiMessage.toolExecutionRequests())
                .attributes(attributes)
                .build();

        // when
        Part sameFunctionCallPart = FunctionCallHelper.toFunctionCallParts(aiMessageWithExtraUnknownField, true)
                .get(0);

        // then
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields().asMap()).containsOnlyKeys(11);
        assertThat(sameFunctionCallPart.getUnknownFields().asMap().get(11))
                .isEqualTo(functionCallPart.getUnknownFields().asMap().get(11));
    }

    @Test
    void should_not_add_attributes_when_return_thinking_is_disabled() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder()
                        .setName("getWeatherForecast")
                        .setArgs(Struct.newBuilder())
                        .build())
                .mergeUnknownFields(unknownFields("thought-signature"))
                .build();

        // when
        AiMessage aiMessage =
                FunctionCallHelper.fromFunctionCallParts(Collections.singletonList(functionCallPart), false);
        Part sameFunctionCallPart =
                FunctionCallHelper.toFunctionCallParts(aiMessage, true).get(0);

        // then
        assertThat(aiMessage.attributes()).isEmpty();
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields().asMap()).isEmpty();
    }

    @Test
    void should_not_send_function_call_part_thought_signature_when_send_thinking_is_disabled() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder()
                        .setName("getWeatherForecast")
                        .setArgs(Struct.newBuilder())
                        .build())
                .mergeUnknownFields(unknownFields("thought-signature"))
                .build();

        // when
        AiMessage aiMessage =
                FunctionCallHelper.fromFunctionCallParts(Collections.singletonList(functionCallPart), true);
        Part sameFunctionCallPart =
                FunctionCallHelper.toFunctionCallParts(aiMessage, false).get(0);

        // then
        assertThat(aiMessage.attributes()).isNotEmpty();
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields().asMap()).isEmpty();
    }

    @Test
    void should_not_add_attributes_when_function_call_part_has_no_thought_signature() {
        // given
        Part functionCallPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder()
                        .setName("getWeatherForecast")
                        .setArgs(Struct.newBuilder())
                        .build())
                .build();

        // when
        AiMessage aiMessage =
                FunctionCallHelper.fromFunctionCallParts(Collections.singletonList(functionCallPart), true);
        Part sameFunctionCallPart =
                FunctionCallHelper.toFunctionCallParts(aiMessage, true).get(0);

        // then
        assertThat(aiMessage.attributes()).isEmpty();
        assertThat(sameFunctionCallPart.getFunctionCall()).isEqualTo(functionCallPart.getFunctionCall());
        assertThat(sameFunctionCallPart.getUnknownFields().asMap()).isEmpty();
    }

    @Test
    void should_preserve_function_call_part_thought_signature_by_position() {
        // given
        Part firstPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder().setName("first").build())
                .build();
        Part secondPart = Part.newBuilder()
                .setFunctionCall(FunctionCall.newBuilder().setName("second").build())
                .mergeUnknownFields(unknownFields("second-thought-signature"))
                .build();

        // when
        AiMessage aiMessage = FunctionCallHelper.fromFunctionCallParts(Arrays.asList(firstPart, secondPart), true);
        List<Part> sameParts = FunctionCallHelper.toFunctionCallParts(aiMessage, true);

        // then
        assertThat(sameParts).hasSize(2);
        assertThat(sameParts.get(0).getUnknownFields().asMap()).isEmpty();
        assertThat(sameParts.get(1).getUnknownFields()).isEqualTo(secondPart.getUnknownFields());
    }

    private static UnknownFieldSet unknownFields(String thoughtSignature) {
        return unknownFields(11, thoughtSignature);
    }

    private static UnknownFieldSet unknownFields(int fieldNumber, String value) {
        return UnknownFieldSet.newBuilder()
                .addField(
                        fieldNumber,
                        UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(ByteString.copyFromUtf8(value))
                                .build())
                .build();
    }
}
