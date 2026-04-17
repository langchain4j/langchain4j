package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiExecutableCode;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiCodeExecution;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiGoogleMaps;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiGoogleSearchRetrieval;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiUrlContext;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlContextMetadata;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GeminiServerToolsMapper {

    static final String SERVER_TOOL_RESULTS_KEY = "server_tool_results";

    private GeminiServerToolsMapper() {}

    static List<GoogleAiGeminiServerTool> mergeServerTools(
            List<GoogleAiGeminiServerTool> serverTools,
            boolean allowCodeExecution,
            boolean allowGoogleSearch,
            boolean allowUrlContext,
            boolean allowGoogleMaps,
            boolean retrieveGoogleMapsWidgetToken) {

        Map<String, GoogleAiGeminiServerTool> merged = new LinkedHashMap<>();

        if (allowCodeExecution) {
            merged.put(
                    "code_execution",
                    GoogleAiGeminiServerTool.builder().type("code_execution").build());
        }
        if (allowGoogleSearch) {
            merged.put(
                    "google_search",
                    GoogleAiGeminiServerTool.builder().type("google_search").build());
        }
        if (allowUrlContext) {
            merged.put(
                    "url_context",
                    GoogleAiGeminiServerTool.builder().type("url_context").build());
        }
        if (allowGoogleMaps) {
            GoogleAiGeminiServerTool.Builder builder =
                    GoogleAiGeminiServerTool.builder().type("google_maps");
            if (retrieveGoogleMapsWidgetToken) {
                builder.addAttribute("enable_widget", true);
            }
            merged.put("google_maps", builder.build());
        }

        for (GoogleAiGeminiServerTool serverTool : copy(serverTools)) {
            merged.put(serverTool.type(), serverTool);
        }

        return new ArrayList<>(merged.values());
    }

    static GeminiTool toGeminiTool(List<GoogleAiGeminiServerTool> serverTools) {
        if (isNullOrEmpty(serverTools)) {
            return null;
        }

        GeminiCodeExecution codeExecution = null;
        GeminiGoogleSearchRetrieval googleSearch = null;
        GeminiUrlContext urlContext = null;
        GeminiGoogleMaps googleMaps = null;

        for (GoogleAiGeminiServerTool serverTool : serverTools) {
            switch (serverTool.type()) {
                case "code_execution" -> codeExecution = new GeminiCodeExecution();
                case "google_search" -> googleSearch = new GeminiGoogleSearchRetrieval();
                case "url_context" -> urlContext = new GeminiUrlContext();
                case "google_maps" ->
                    googleMaps = new GeminiGoogleMaps(booleanAttribute(serverTool.attributes(), "enable_widget"));
                default ->
                    throw new UnsupportedFeatureException(
                            "Unsupported Google AI Gemini server tool type: " + serverTool.type()
                                    + ". Supported types are: code_execution, google_search, url_context, google_maps.");
            }
        }

        return new GeminiTool(null, codeExecution, googleSearch, urlContext, googleMaps);
    }

    static List<GoogleAiGeminiServerToolResult> extractServerToolResults(
            List<GeminiPart> parts, GeminiUrlContextMetadata urlContextMetadata, GroundingMetadata groundingMetadata) {
        List<GoogleAiGeminiServerToolResult> results = new ArrayList<>();

        List<Map<String, Object>> codeExecutionSteps = extractCodeExecutionSteps(parts);
        if (!codeExecutionSteps.isEmpty()) {
            results.add(GoogleAiGeminiServerToolResult.builder()
                    .type("code_execution_tool_result")
                    .content(codeExecutionSteps)
                    .build());
        }

        if (urlContextMetadata != null && !isNullOrEmpty(urlContextMetadata.urlMetadata())) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("url_metadata", urlContextMetadata.urlMetadata());
            results.add(GoogleAiGeminiServerToolResult.builder()
                    .type("url_context_tool_result")
                    .content(content)
                    .build());
        }

        if (hasGoogleSearchData(groundingMetadata)) {
            Map<String, Object> content = new LinkedHashMap<>();
            putIfNotNull(content, "web_search_queries", groundingMetadata.webSearchQueries());
            putIfNotNull(content, "search_entry_point", groundingMetadata.searchEntryPoint());
            putIfNotNull(content, "retrieval_metadata", groundingMetadata.retrievalMetadata());
            putIfNotNull(content, "grounding_chunks", groundingMetadata.groundingChunks());
            putIfNotNull(content, "grounding_supports", groundingMetadata.groundingSupports());
            results.add(GoogleAiGeminiServerToolResult.builder()
                    .type("google_search_tool_result")
                    .content(content)
                    .build());
        }

        if (hasGoogleMapsData(groundingMetadata)) {
            Map<String, Object> content = new LinkedHashMap<>();
            putIfNotNull(content, "grounding_chunks", groundingMetadata.groundingChunks());
            putIfNotNull(content, "grounding_supports", groundingMetadata.groundingSupports());
            putIfNotNull(content, "google_maps_widget_context_token", groundingMetadata.googleMapsWidgetContextToken());
            results.add(GoogleAiGeminiServerToolResult.builder()
                    .type("google_maps_tool_result")
                    .content(content)
                    .build());
        }

        return results;
    }

    static List<GoogleAiGeminiServerToolResult> extractServerToolResults(
            GeminiGenerateContentResponse response, GeminiCandidate candidate) {
        List<GeminiPart> parts = candidate != null && candidate.content() != null
                ? candidate.content().parts()
                : List.of();
        GroundingMetadata groundingMetadata = response != null && response.groundingMetadata() != null
                ? response.groundingMetadata()
                : candidate != null ? candidate.groundingMetadata() : null;
        GeminiUrlContextMetadata urlContextMetadata = candidate != null ? candidate.urlContextMetadata() : null;
        return extractServerToolResults(parts, urlContextMetadata, groundingMetadata);
    }

    private static List<Map<String, Object>> extractCodeExecutionSteps(List<GeminiPart> parts) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (parts == null) {
            return steps;
        }

        for (GeminiPart part : parts) {
            GeminiExecutableCode executableCode = part.executableCode();
            if (executableCode != null) {
                Map<String, Object> code = new LinkedHashMap<>();
                putIfNotNull(
                        code,
                        "programming_language",
                        executableCode.programmingLanguage() != null
                                ? executableCode.programmingLanguage().toString()
                                : null);
                putIfNotNull(code, "code", executableCode.code());
                steps.add(code);
            }

            GeminiCodeExecutionResult codeExecutionResult = part.codeExecutionResult();
            if (codeExecutionResult != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                putIfNotNull(
                        result,
                        "outcome",
                        codeExecutionResult.outcome() != null
                                ? codeExecutionResult.outcome().toString()
                                : null);
                putIfNotNull(result, "output", codeExecutionResult.output());
                steps.add(result);
            }
        }

        return steps;
    }

    private static boolean hasGoogleSearchData(GroundingMetadata groundingMetadata) {
        if (groundingMetadata == null) {
            return false;
        }
        if (!isNullOrEmpty(groundingMetadata.webSearchQueries())
                || groundingMetadata.searchEntryPoint() != null
                || groundingMetadata.retrievalMetadata() != null) {
            return true;
        }
        if (isNullOrEmpty(groundingMetadata.groundingChunks())) {
            return false;
        }
        return groundingMetadata.groundingChunks().stream()
                .anyMatch(chunk -> chunk.web() != null || chunk.retrievedContext() != null);
    }

    private static boolean hasGoogleMapsData(GroundingMetadata groundingMetadata) {
        if (groundingMetadata == null) {
            return false;
        }
        if (groundingMetadata.googleMapsWidgetContextToken() != null) {
            return true;
        }
        if (isNullOrEmpty(groundingMetadata.groundingChunks())) {
            return false;
        }
        return groundingMetadata.groundingChunks().stream().anyMatch(chunk -> chunk.maps() != null);
    }

    private static boolean booleanAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
