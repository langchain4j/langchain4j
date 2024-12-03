package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static java.util.Collections.emptyMap;

class FunctionMapper {

    private static final Gson GSON = new Gson();

    static GeminiTool fromToolSepcsToGTool(List<ToolSpecification> specifications, boolean allowCodeExecution) {

        GeminiTool.GeminiToolBuilder tool = GeminiTool.builder();

        if (allowCodeExecution) {
            tool.codeExecution(new GeminiCodeExecution());
        }

        if (specifications == null || specifications.isEmpty()) {
            if (allowCodeExecution) {
                // if there's no tool specification, but there's Python code execution
                return tool.build();
            } else {
                // if there's neither tool specification nor Python code execution
                return null;
            }
        }

        List<GeminiFunctionDeclaration> functionDeclarations = specifications.stream()
            .map(specification -> {
                GeminiFunctionDeclaration.GeminiFunctionDeclarationBuilder fnBuilder =
                    GeminiFunctionDeclaration.builder()
                            .name(specification.name());

                    if (specification.description() != null) {
                        fnBuilder.description(specification.description());
                    }

                    if (specification.parameters() != null) {
                        fnBuilder.parameters(fromJsonSchemaToGSchema(specification.parameters()));
                    } else if (specification.toolParameters() != null) {
                        ToolParameters toolParameters = specification.toolParameters();
                        String description = specification.description();
                        Map<String, Map<String, Object>> properties = toolParameters.properties();
                        fnBuilder.parameters(fromMap("object", null, description, properties));
                    }

                return fnBuilder.build();
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (!functionDeclarations.isEmpty()) {
            tool.functionDeclarations(functionDeclarations);
        }

        return tool.build();
    }

    private static GeminiSchema fromMap(String type, String arrayType, String description, Map<String, Map<String, Object>> obj) {
        GeminiSchema.GeminiSchemaBuilder schemaBuilder = GeminiSchema.builder();

        schemaBuilder.type(GeminiType.valueOf(type.toUpperCase()));
        schemaBuilder.description(description);

        if (type.equals("array")) {
            Map<String, Map<String, Object>> arrayObj = (Map<String, Map<String, Object>>) obj.values().iterator().next().get("properties");

            schemaBuilder.items(fromMap(arrayType, null, description, arrayObj));
        } else {
            Map<String, GeminiSchema> props = new LinkedHashMap<>();
            if (obj != null) {
                for (Map.Entry<String, Map<String, Object>> oneProperty : obj.entrySet()) {
                    String propName = oneProperty.getKey();
                    Map<String, Object> propAttributes = oneProperty.getValue();
                    String propTypeString = (String) propAttributes.getOrDefault("type", "string");
                    String propDescription = (String) propAttributes.getOrDefault("description", null);
                    Map<String, Map<String, Object>> childProps =
                        (Map<String, Map<String, Object>>) propAttributes.getOrDefault("properties", emptyMap());
                    Map<String, Object> items = (Map<String, Object>) propAttributes.get("items");
                    Map<String, Map<String, Object>> singleProp = new HashMap<>();
                    singleProp.put(propName, items);

                    if (items != null) {
                        String itemsType = items.get("type").toString();
                        props.put(propName, fromMap(propTypeString, itemsType, propDescription, singleProp));
                    } else {
                        props.put(propName, fromMap(propTypeString, null, propDescription, childProps));
                    }
                }
            }
            schemaBuilder.properties(props);
        }

        return schemaBuilder.build();
    }

    static List<ToolExecutionRequest> fromToolExecReqToGFunCall(List<GeminiFunctionCall> functionCalls) {
        return functionCalls.stream()
            .map(functionCall -> ToolExecutionRequest.builder()
                .name(functionCall.getName())
                .arguments(GSON.toJson(functionCall.getArgs()))
                .build())
            .collect(Collectors.toList());
    }
}
