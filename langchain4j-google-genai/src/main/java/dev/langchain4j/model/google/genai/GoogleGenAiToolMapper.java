package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.Type;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GoogleGenAiToolMapper {

    static Tool convertToGoogleTool(ToolSpecification spec) {
        Schema parameterSchema = convertToGoogleSchema(spec.parameters());

        FunctionDeclaration.Builder fdBuilder = FunctionDeclaration.builder()
                .name(spec.name())
                .description(isNullOrBlank(spec.description()) ? "" : spec.description());
        if (parameterSchema != null) {
            fdBuilder.parameters(parameterSchema);
        }
        FunctionDeclaration functionDeclaration = fdBuilder.build();

        return Tool.builder().functionDeclarations(List.of(functionDeclaration)).build();
    }

    static FunctionDeclaration convertToGoogleFunction(ToolSpecification spec) {
        Schema parameterSchema = convertToGoogleSchema(spec.parameters());
        FunctionDeclaration.Builder fdBuilder =
                FunctionDeclaration.builder().name(spec.name()).description(getOrDefault(spec.description(), ""));
        if (parameterSchema != null) {
            fdBuilder.parameters(parameterSchema);
        }
        return fdBuilder.build();
    }

    static Schema convertToGoogleSchema(JsonSchemaElement element) {
        if (element == null) {
            return null;
        }

        if (element instanceof JsonObjectSchema objectSchema) {
            Map<String, Schema> properties = new HashMap<>();
            if (objectSchema.properties() != null) {
                objectSchema
                        .properties()
                        .forEach((String key, JsonSchemaElement value) ->
                                properties.put(key, convertToGoogleSchema(value)));
            }

            return Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(properties)
                    .required(getOrDefault(objectSchema.required(), Collections.emptyList()))
                    .description(getOrDefault(objectSchema.description(), ""))
                    .build();
        }

        if (element instanceof JsonStringSchema stringSchema) {
            return Schema.builder()
                    .type(Type.Known.STRING)
                    .description(getOrDefault(stringSchema.description(), ""))
                    .build();
        }

        if (element instanceof JsonEnumSchema enumSchema) {
            return Schema.builder()
                    .type(Type.Known.STRING)
                    .format("enum")
                    .enum_(enumSchema.enumValues())
                    .description(getOrDefault(enumSchema.description(), ""))
                    .build();
        }

        if (element instanceof JsonIntegerSchema) {
            return Schema.builder()
                    .type(Type.Known.INTEGER)
                    .description(getOrDefault(element.description(), ""))
                    .build();
        }

        if (element instanceof JsonNumberSchema) {
            return Schema.builder()
                    .type(Type.Known.NUMBER)
                    .description(getOrDefault(element.description(), ""))
                    .build();
        }

        if (element instanceof JsonBooleanSchema) {
            return Schema.builder()
                    .type(Type.Known.BOOLEAN)
                    .description(getOrDefault(element.description(), ""))
                    .build();
        }

        if (element instanceof JsonArraySchema arraySchema) {
            return Schema.builder()
                    .type(Type.Known.ARRAY)
                    .items(convertToGoogleSchema(arraySchema.items()))
                    .description(getOrDefault(arraySchema.description(), ""))
                    .build();
        }

        throw new IllegalArgumentException("Unknown schema type: " + element.getClass());
    }
}
