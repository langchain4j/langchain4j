package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.googleai.Json.toJsonWithoutIndent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFunctionCall;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiCodeExecution;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiGoogleMaps;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiGoogleSearchRetrieval;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiUrlContext;
import java.util.List;
import java.util.Objects;

class FunctionMapper {
    static GeminiTool fromToolSepcsToGTool(
            List<ToolSpecification> specifications,
            boolean allowCodeExecution,
            boolean allowGoogleSearch,
            boolean allowUrlContext,
            boolean allowGoogleMaps,
            boolean retrieveGoogleMapsWidgetToken) {
        if (isNullOrEmpty(specifications)) {
            if (allowCodeExecution || allowGoogleSearch || allowUrlContext || allowGoogleMaps) {
                // if there's no tool specification, but there's Python code execution or Google Search retrieval
                // or URL context or Google Maps
                return new GeminiTool(
                        null,
                        allowCodeExecution ? new GeminiCodeExecution() : null,
                        allowGoogleSearch ? new GeminiGoogleSearchRetrieval() : null,
                        allowUrlContext ? new GeminiUrlContext() : null,
                        allowGoogleMaps ? new GeminiGoogleMaps(retrieveGoogleMapsWidgetToken) : null);
            } else {
                // if there's neither tool specification nor Python code execution nor URL context nor Google Search
                // retrieval
                return null;
            }
        }

        List<GeminiFunctionDeclaration> functionDeclarations = specifications.stream()
                .map(specification -> {
                    GeminiFunctionDeclaration.Builder fnBuilder =
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
                allowCodeExecution ? new GeminiCodeExecution() : null,
                allowGoogleSearch ? new GeminiGoogleSearchRetrieval() : null,
                allowUrlContext ? new GeminiUrlContext() : null,
                allowGoogleMaps ? new GeminiGoogleMaps(retrieveGoogleMapsWidgetToken) : null);
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
