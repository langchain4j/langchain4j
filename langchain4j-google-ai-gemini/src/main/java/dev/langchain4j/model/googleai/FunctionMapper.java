package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;

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
        return functionCalls.stream()
            .map(functionCall -> ToolExecutionRequest.builder()
                .name(functionCall.getName())
                .arguments(GSON.toJson(functionCall.getArgs()))
                .build())
            .collect(Collectors.toList());
    }
}
