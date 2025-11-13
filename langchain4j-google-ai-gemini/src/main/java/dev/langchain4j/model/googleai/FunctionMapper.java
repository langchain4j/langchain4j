package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.googleai.Json.toJsonWithoutIndent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFunctionCall;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiCodeExecution;
import java.util.List;
import java.util.Objects;

class FunctionMapper {

    static GeminiTool fromToolSepcsToGTool(List<ToolSpecification> specifications, boolean allowCodeExecution) {
        if (isNullOrEmpty(specifications)) {
            if (allowCodeExecution) {
                // if there's no tool specification, but there's Python code execution
                return new GeminiTool(null, new GeminiCodeExecution());
            } else {
                // if there's neither tool specification nor Python code execution
                return null;
            }
        }

        List<GeminiFunctionDeclaration> functionDeclarations = specifications.stream()
                .map(specification -> {
                    GeminiFunctionDeclaration.GeminiFunctionDeclarationBuilder fnBuilder =
                            GeminiFunctionDeclaration.builder().name(specification.name());

                    if (specification.description() != null) {
                        fnBuilder.description(specification.description());
                    }

                    if (specification.parameters() != null) {
                        fnBuilder.parameters(fromJsonSchemaToGSchema(specification.parameters()));
                    }

                    return fnBuilder.build();
                })
                .filter(Objects::nonNull)
                .toList();

        return new GeminiTool(
                functionDeclarations.isEmpty() ? null : functionDeclarations,
                allowCodeExecution ? new GeminiCodeExecution() : null);
    }

    static List<ToolExecutionRequest> toToolExecutionRequests(List<GeminiFunctionCall> functionCalls) {
        return functionCalls.stream()
                .map(functionCall -> ToolExecutionRequest.builder()
                        .name(functionCall.name())
                        .arguments(toJsonWithoutIndent(functionCall.args()))
                        .build())
                .toList();
    }
}
