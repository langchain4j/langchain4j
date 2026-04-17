package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.googleai.Json.toJsonWithoutIndent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static java.util.Collections.singletonList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFunctionCall;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import java.util.List;
import java.util.Objects;

class FunctionMapper {
    static List<GeminiTool> fromToolSpecsToGTools(
            List<ToolSpecification> specifications,
            boolean allowCodeExecution,
            boolean allowGoogleSearch,
            boolean allowUrlContext,
            boolean allowGoogleMaps,
            boolean retrieveGoogleMapsWidgetToken) {
        List<GoogleAiGeminiServerTool> serverTools = GeminiServerToolsMapper.mergeServerTools(
                List.of(),
                allowCodeExecution,
                allowGoogleSearch,
                allowUrlContext,
                allowGoogleMaps,
                retrieveGoogleMapsWidgetToken);
        return fromToolSpecsToGTools(specifications, serverTools);
    }

    static List<GeminiTool> fromToolSpecsToGTools(
            List<ToolSpecification> specifications, List<GoogleAiGeminiServerTool> serverTools) {
        return fromToolSpecsToGTools(specifications, serverTools, false, false, false, false, false);
    }

    static List<GeminiTool> fromToolSpecsToGTools(
            List<ToolSpecification> specifications,
            List<GoogleAiGeminiServerTool> serverTools,
            boolean allowCodeExecution,
            boolean allowGoogleSearch,
            boolean allowUrlContext,
            boolean allowGoogleMaps,
            boolean retrieveGoogleMapsWidgetToken) {

        List<GoogleAiGeminiServerTool> mergedServerTools = GeminiServerToolsMapper.mergeServerTools(
                serverTools,
                allowCodeExecution,
                allowGoogleSearch,
                allowUrlContext,
                allowGoogleMaps,
                retrieveGoogleMapsWidgetToken);

        List<GeminiFunctionDeclaration> functionDeclarations = isNullOrEmpty(specifications)
                ? List.of()
                : specifications.stream()
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

        GeminiTool serverTool = GeminiServerToolsMapper.toGeminiTool(mergedServerTools);
        if (functionDeclarations.isEmpty() && serverTool == null) {
            return null;
        }

        return singletonList(new GeminiTool(
                functionDeclarations.isEmpty() ? null : functionDeclarations,
                serverTool != null ? serverTool.codeExecution() : null,
                serverTool != null ? serverTool.googleSearch() : null,
                serverTool != null ? serverTool.urlContext() : null,
                serverTool != null ? serverTool.googleMaps() : null));
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
