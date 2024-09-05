package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.*;
import java.util.stream.Collectors;

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
                    GeminiFunctionDeclaration.builder();
                if (specification.name() != null) {
                    fnBuilder.name(specification.name());
                }
                if (specification.description() != null) {
                    fnBuilder.description(specification.description());
                }
                if (specification.parameters() != null) {
                    ToolParameters parameters = specification.parameters();

                    final String[] propName = {""};
                    fnBuilder.parameters(GeminiSchema.builder()
                        .type(GeminiType.OBJECT)
                        .properties(parameters.properties().entrySet().stream()
                            .map(prop -> {
                                propName[0] = prop.getKey();
                                Map<String, Object> propAttributes = prop.getValue();

                                String typeString = (String) propAttributes.getOrDefault("type", "string");
                                GeminiType type = GeminiType.valueOf(typeString.toUpperCase());
                                String description = (String) propAttributes.getOrDefault("description", null);

                                //TODO need to deal with nested objects

                                return GeminiSchema.builder()
                                    .description(description)
                                    .type(type)
                                    .build();
                            })
                            .collect(Collectors.toMap(schema -> propName[0], schema -> schema)))
                        .build());
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

    static List<ToolExecutionRequest> fromToolExecReqToGFunCall(List<GeminiFunctionCall> functionCalls) {
        return functionCalls.stream().map(functionCall -> ToolExecutionRequest.builder()
            .name(functionCall.getName())
            .arguments(GSON.toJson(functionCall.getArgs()))
            .build()).collect(Collectors.toList());
    }
}
