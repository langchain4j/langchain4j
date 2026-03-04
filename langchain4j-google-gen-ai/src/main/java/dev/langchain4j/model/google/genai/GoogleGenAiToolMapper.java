package dev.langchain4j.model.google.genai;

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
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleGenAiToolMapper {

    public static Tool convertToGoogleTool(ToolSpecification spec) {
        // 1. Convert the parameters (JsonSchema) to Google's Schema type
        Schema parameterSchema = convertToGoogleSchema(spec.parameters());

        // 2. Create the FunctionDeclaration
        FunctionDeclaration.Builder fdBuilder = FunctionDeclaration.builder()
                .name(spec.name())
                .description(!isNullOrBlank(spec.description()) ? spec.description() : "");
        if (parameterSchema != null) {
            fdBuilder.parameters(parameterSchema);
        }
        FunctionDeclaration functionDeclaration = fdBuilder.build();

        // 3. Wrap in a Tool object
        return Tool.builder().functionDeclarations(List.of(functionDeclaration)).build();
    }

    public static FunctionDeclaration convertToGoogleFunction(ToolSpecification spec) {
        Schema parameterSchema = convertToGoogleSchema(spec.parameters());
        FunctionDeclaration.Builder fdBuilder = FunctionDeclaration.builder()
                .name(spec.name())
                .description(spec.description() != null ? spec.description() : "");
        if (parameterSchema != null) {
            fdBuilder.parameters(parameterSchema);
        }
        return fdBuilder.build();
    }

    private static Schema convertToGoogleSchema(JsonSchemaElement element) {
        if (element == null) {
            return null;
        }

        // --- OBJECT ---
        if (element instanceof JsonObjectSchema) {
            JsonObjectSchema objectSchema = (JsonObjectSchema) element;
            Map<String, Schema> properties = new HashMap<>();

            // Recursively convert properties
            if (objectSchema.properties() != null) {
                objectSchema.properties().forEach((String key, JsonSchemaElement value) -> properties.put(key, convertToGoogleSchema(value)));
            }

            return Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(properties)
                    .required(objectSchema.required() != null ? objectSchema.required() : Collections.emptyList())
                    .description(objectSchema.description() != null ? objectSchema.description() : "")
                    .build();
        }

        // --- STRING / ENUM ---
        if (element instanceof JsonStringSchema) {
            JsonStringSchema stringSchema = (JsonStringSchema) element;
            return Schema.builder()
                    .type(Type.Known.STRING)
                    .description(stringSchema.description() != null ? stringSchema.description() : "")
                    .build();
        }

        if (element instanceof JsonEnumSchema) {
            JsonEnumSchema enumSchema = (JsonEnumSchema) element;
            return Schema.builder()
                    .type(Type.Known.STRING)
                    .format("enum")
                    .enum_(enumSchema.enumValues())
                    .description(enumSchema.description() != null ? enumSchema.description() : "")
                    .build();
        }

        // --- INTEGER ---
        if (element instanceof JsonIntegerSchema) {
            return Schema.builder()
                    .type(Type.Known.INTEGER)
                    .description(element.description() != null ? element.description() : "")
                    .build();
        }

        // --- NUMBER ---
        if (element instanceof JsonNumberSchema) {
            return Schema.builder()
                    .type(Type.Known.NUMBER)
                    .description(element.description() != null ? element.description() : "")
                    .build();
        }

        // --- BOOLEAN ---
        if (element instanceof JsonBooleanSchema) {
            return Schema.builder()
                    .type(Type.Known.BOOLEAN)
                    .description(element.description() != null ? element.description() : "")
                    .build();
        }

        // --- ARRAY ---
        if (element instanceof JsonArraySchema) {
            JsonArraySchema arraySchema = (JsonArraySchema) element;
            return Schema.builder()
                    .type(Type.Known.ARRAY)
                    .items(convertToGoogleSchema(arraySchema.items()))
                    .description(arraySchema.description() != null ? arraySchema.description() : "")
                    .build();
        }

        throw new IllegalArgumentException("Unknown schema type: " + element.getClass());
    }
}
