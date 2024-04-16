package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import dev.langchain4j.exception.JsonSchemaGenerationException;
import dev.langchain4j.exception.JsonSchemaSerializationException;
import dev.langchain4j.jsonschema.JsonSchema;
import dev.langchain4j.jsonschema.JsonSchemaServiceFactories;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolJsonSchemas {
    private final Map<Method, ToolJsonSchema> toolJsonSchemas;
    private final JsonSchemaServiceFactories.Service jsonSchemaService;

    public ToolJsonSchemas() {
        this(JsonSchemaServiceFactories.DEFAULT_SERVICE);
    }

    public ToolJsonSchemas(JsonSchemaServiceFactories.Service jsonSchemaService) {
        this.toolJsonSchemas = new java.util.concurrent.ConcurrentHashMap<>();
        this.jsonSchemaService = jsonSchemaService;
    }

    /**
     * Get the {@link ToolJsonSchema}s for each {@link Tool} method of the given object.
     *
     * @param objectWithTools the object.
     * @return the {@link ToolJsonSchema}s.
     */
    public List<ToolJsonSchema> toToolJsonSchemas(Object objectWithTools) {
        return stream(objectWithTools.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(this::toToolJsonSchema)
                .collect(toList());
    }

    public ToolJsonSchema toToolJsonSchema(Method method) {
        return toolJsonSchemas.computeIfAbsent(method, this::generateToolJsonSchema);
    }

    public String serialize(Map<String, Object> arguments) {
        try {
            return jsonSchemaService.serialize(arguments);
        } catch (JsonSchemaSerializationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Map<String, Object> deserialize(String arguments, Method method)
            throws JsonSchemaDeserializationException {
        JsonObject argumentsJsonObject = JsonParser.parseString(arguments).getAsJsonObject();
        ToolJsonSchema toolJsonSchema = toToolJsonSchema(method);

        Map<String, Object> deserializedArguments = new HashMap<>();
        for (Map.Entry<String, JsonSchema> parameter : toolJsonSchema.parameters().entrySet()) {
            JsonElement argumentJsonElement = argumentsJsonObject.get(parameter.getKey());
            deserializedArguments.put(
                    parameter.getKey(),
                    argumentJsonElement == null
                            ? null
                            : jsonSchemaService.deserialize(
                                    argumentJsonElement.toString(), parameter.getValue()));
        }
        return deserializedArguments;
    }

    ToolJsonSchema generateToolJsonSchema(Method method) {
        String name = method.getName();
        String description = "";

        Tool annotation = method.getAnnotation(Tool.class);
        if (annotation != null) {
            name = isNullOrBlank(annotation.name()) ? name : annotation.name();
            description = String.join("\n", annotation.value());
        }

        Map<String, JsonSchema> paramSchemas = new LinkedHashMap<>();
        for (Parameter param : method.getParameters()) {
            if (!param.isAnnotationPresent(ToolMemoryId.class)) {
                String paramName = param.getName();
                P pAnnotation = param.getAnnotation(P.class);
                JsonSchema paramSchema;
                try {
                    paramSchema =
                            this.jsonSchemaService
                                    .generate(param.getParameterizedType())
                                    .addDescription(
                                            pAnnotation == null ? null : pAnnotation.value());
                } catch (JsonSchemaGenerationException e) {
                    throw new IllegalArgumentException(e);
                }
                paramSchemas.put(paramName, paramSchema);
            }
        }

        return new ToolJsonSchema(name, description, paramSchemas);
    }
}
