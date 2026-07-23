package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.google.cloud.vertexai.api.*;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class FunctionCallHelper {

    private static final Gson GSON = new Gson();
    private static final int THOUGHT_SIGNATURE_FIELD_NUMBER = 11;
    private static final String FUNCTION_CALL_PART_THOUGHT_SIGNATURES_KEY =
            "vertex_ai_gemini_function_call_part_thought_signatures";

    static FunctionCall fromToolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
        FunctionCall.Builder fnCallBuilder = FunctionCall.newBuilder().setName(toolExecutionRequest.name());

        Struct.Builder structBuilder = Struct.newBuilder();
        try {
            String toolArguments = toolExecutionRequest.arguments();
            String arguments = isNullOrBlank(toolArguments) ? "{}" : toolArguments;
            JsonFormat.parser().merge(arguments, structBuilder);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        Struct argsStruct = structBuilder.build();
        fnCallBuilder.setArgs(argsStruct);

        return fnCallBuilder.build();
    }

    static List<ToolExecutionRequest> fromFunctionCalls(List<FunctionCall> functionCalls) {
        return IntStream.range(0, functionCalls.size())
                .mapToObj(index -> fromFunctionCall(index, functionCalls.get(index)))
                .toList();
    }

    static AiMessage fromFunctionCallParts(List<Part> functionCallParts, boolean returnThinking) {
        List<ToolExecutionRequest> toolExecutionRequests = IntStream.range(0, functionCallParts.size())
                .mapToObj(index ->
                        fromFunctionCall(index, functionCallParts.get(index).getFunctionCall()))
                .toList();

        Map<String, Object> attributes = returnThinking ? attributesFromFunctionCallParts(functionCallParts) : Map.of();

        return AiMessage.builder()
                .toolExecutionRequests(toolExecutionRequests)
                .attributes(attributes)
                .build();
    }

    static List<Part> toFunctionCallParts(AiMessage aiMessage, boolean sendThinking) {
        List<String> encodedThoughtSignatures = sendThinking ? functionCallPartThoughtSignatures(aiMessage) : List.of();
        List<Part> parts = new ArrayList<>(aiMessage.toolExecutionRequests().size());

        for (int i = 0; i < aiMessage.toolExecutionRequests().size(); i++) {
            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(i);
            Part.Builder partBuilder =
                    Part.newBuilder().setFunctionCall(fromToolExecutionRequest(toolExecutionRequest));

            UnknownFieldSet thoughtSignature = decodeThoughtSignature(encodedThoughtSignatures, i);
            if (!thoughtSignature.asMap().isEmpty()) {
                partBuilder.mergeUnknownFields(thoughtSignature);
            }

            parts.add(partBuilder.build());
        }

        return parts;
    }

    /**
     * Converts a FunctionCall to a ToolExecutionRequest.
     * The index is the position of the FunctionCall in the list of FunctionCalls returned by the Gemini API. Based on
     * the Gemini API doc, to send the results back, include the responses in the same order as they were requested.
     * So the index can be used as the id of the ToolExecutionRequest. The id is unique for each ToolExecutionRequest
     * in the response of the model.
     * @param index the index of the FunctionCall in the list of FunctionCalls returned by the Gemini API
     * @param functionCall the FunctionCall to convert
     * @see <a href="https://ai.google.dev/gemini-api/docs/function-calling?example=meeting#parallel_function_calling">
     *     Gemini API Function Calling</a>
     * @return a ToolExecutionRequest corresponding to the FunctionCall
     */
    static ToolExecutionRequest fromFunctionCall(final int index, FunctionCall functionCall) {
        ToolExecutionRequest.Builder builder =
                ToolExecutionRequest.builder().id(String.valueOf(index)).name(functionCall.getName());

        Map<String, Object> callArgsMap = new HashMap<>();
        Struct callArgs = functionCall.getArgs();
        Map<String, Value> callArgsFieldsMap = callArgs.getFieldsMap();
        callArgsFieldsMap.forEach((key, value) -> callArgsMap.put(key, unwrapProtoValue(value)));

        String serializedArgsMap = GSON.toJson(callArgsMap);
        builder.arguments(serializedArgsMap);

        return builder.build();
    }

    static Object unwrapProtoValue(Value value) {
        Object unwrappedValue;
        switch (value.getKindCase()) {
            case NUMBER_VALUE:
                unwrappedValue = value.getNumberValue();
                break;
            case STRING_VALUE:
                unwrappedValue = value.getStringValue();
                break;
            case BOOL_VALUE:
                unwrappedValue = value.getBoolValue();
                break;
            case STRUCT_VALUE:
                HashMap<String, Object> mapForStruct = new HashMap<>();
                value.getStructValue()
                        .getFieldsMap()
                        .forEach((key, val) -> mapForStruct.put(key, unwrapProtoValue(val)));
                unwrappedValue = mapForStruct;
                break;
            case LIST_VALUE:
                unwrappedValue = value.getListValue().getValuesList().stream()
                        .map(FunctionCallHelper::unwrapProtoValue)
                        .collect(Collectors.toList());
                break;
            default: // NULL_VALUE, KIND_NOT_SET, and default
                unwrappedValue = null;
                break;
        }
        return unwrappedValue;
    }

    private static Map<String, Object> attributesFromFunctionCallParts(List<Part> functionCallParts) {
        List<String> encodedThoughtSignatures = functionCallParts.stream()
                .map(FunctionCallHelper::thoughtSignatureUnknownField)
                .map(UnknownFieldSet::toByteArray)
                .map(bytes -> Base64.getEncoder().encodeToString(bytes))
                .toList();

        boolean hasThoughtSignatures = encodedThoughtSignatures.stream().anyMatch(encoded -> !encoded.isEmpty());
        if (!hasThoughtSignatures) {
            return Map.of();
        }

        return Map.of(FUNCTION_CALL_PART_THOUGHT_SIGNATURES_KEY, encodedThoughtSignatures);
    }

    private static UnknownFieldSet thoughtSignatureUnknownField(Part part) {
        return thoughtSignatureUnknownField(part.getUnknownFields());
    }

    private static UnknownFieldSet thoughtSignatureUnknownField(UnknownFieldSet unknownFields) {
        UnknownFieldSet.Field thoughtSignature = unknownFields.asMap().get(THOUGHT_SIGNATURE_FIELD_NUMBER);
        if (thoughtSignature == null
                || thoughtSignature.getLengthDelimitedList().isEmpty()) {
            return UnknownFieldSet.getDefaultInstance();
        }

        return UnknownFieldSet.newBuilder()
                .addField(THOUGHT_SIGNATURE_FIELD_NUMBER, thoughtSignature)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> functionCallPartThoughtSignatures(AiMessage aiMessage) {
        Object encodedThoughtSignatures = aiMessage.attributes().get(FUNCTION_CALL_PART_THOUGHT_SIGNATURES_KEY);
        if (encodedThoughtSignatures instanceof List
                && ((List<?>) encodedThoughtSignatures).stream().allMatch(String.class::isInstance)) {
            return (List<String>) encodedThoughtSignatures;
        }
        return List.of();
    }

    private static UnknownFieldSet decodeThoughtSignature(List<String> encodedThoughtSignatures, int index) {
        if (index >= encodedThoughtSignatures.size() || isNullOrBlank(encodedThoughtSignatures.get(index))) {
            return UnknownFieldSet.getDefaultInstance();
        }

        try {
            return thoughtSignatureUnknownField(
                    UnknownFieldSet.parseFrom(Base64.getDecoder().decode(encodedThoughtSignatures.get(index))));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    static Tool convertToolSpecifications(List<ToolSpecification> toolSpecifications) {
        Tool.Builder tool = Tool.newBuilder();

        for (ToolSpecification toolSpecification : toolSpecifications) {
            FunctionDeclaration.Builder fnBuilder =
                    FunctionDeclaration.newBuilder().setName(toolSpecification.name());

            if (toolSpecification.description() != null) {
                fnBuilder.setDescription(toolSpecification.description());
            }

            if (toolSpecification.parameters() != null) {
                fnBuilder.setParameters(SchemaHelper.from(toolSpecification.parameters()));
            }

            tool.addFunctionDeclarations(fnBuilder.build());
        }

        return tool.build();
    }
}
