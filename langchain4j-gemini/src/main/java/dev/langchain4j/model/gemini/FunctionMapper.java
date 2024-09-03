package dev.langchain4j.model.gemini;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.*;
import java.util.stream.Collectors;

class FunctionMapper {

    static GeminiTool fromToolSepcsToGTool(List<ToolSpecification> specifications) {
        if (specifications == null) {
            return null;
        }

        GeminiTool.GeminiToolBuilder tool = GeminiTool.builder();

        List<GeminiFunctionDeclaration> functionDeclarations = specifications.stream()
            .map(specification -> {
                // using Gemini's built-in hosted Python sandboxed code executor
                if (specification.name().equals("code_execution")) {
                    tool.codeExecution(new GeminiCodeExecution());
                    return null;
                }

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

    public static List<ToolExecutionRequest> fromToolExecReqToGFunCall(List<GeminiFunctionCall> functionCalls) {
        return functionCalls.stream().map(functionCall -> ToolExecutionRequest.builder()
            .name(functionCall.getName())
            .arguments(new Gson().toJson(functionCall.getArgs()))
            .build()).collect(Collectors.toList());
    }
}
