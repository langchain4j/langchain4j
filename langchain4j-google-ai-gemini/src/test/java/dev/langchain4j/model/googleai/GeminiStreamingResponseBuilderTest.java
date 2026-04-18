package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiStreamingResponseBuilder.TextAndTools;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiStreamingResponseBuilderTest {

    private final GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null, false);

    @Test
    void should_return_empty_when_partial_response_is_null() {
        TextAndTools result = builder.append(null);

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.maybeThought()).isEmpty();
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_return_empty_when_candidates_is_null() {
        GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(null, null, null, null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.maybeThought()).isEmpty();
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_return_empty_when_candidates_is_empty() {
        GeminiGenerateContentResponse response =
                new GeminiGenerateContentResponse(null, null, Collections.emptyList(), null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.maybeThought()).isEmpty();
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_return_text_when_candidate_has_content() {
        GeminiContent content = new GeminiContent(
                List.of(GeminiContent.GeminiPart.builder().text("Hello").build()), "model");
        GeminiCandidate candidate = new GeminiCandidate(content, null, null, null);
        GeminiGenerateContentResponse response =
                new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).hasValue("Hello");
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_include_server_tool_results_when_enabled() {
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null, true);
        GeminiContent content = new GeminiContent(
                List.of(GeminiContent.GeminiPart.builder()
                        .executableCode(new GeminiContent.GeminiPart.GeminiExecutableCode(
                                GeminiContent.GeminiPart.GeminiExecutableCode.GeminiLanguage.PYTHON,
                                "print(1)",
                                "code-id"))
                        .codeExecutionResult(new GeminiContent.GeminiPart.GeminiCodeExecutionResult(
                                GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome.OUTCOME_OK,
                                "1",
                                "code-id"))
                        .build()),
                "model");
        GeminiCandidate candidate = new GeminiCandidate(content, null, null, null);
        GeminiGenerateContentResponse response =
                new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null);

        builder.append(response);

        ChatResponse completeResponse = builder.build();

        assertThat(completeResponse.aiMessage().attributes())
                .containsKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
    }

    @Test
    void should_preserve_raw_tool_circulation_parts_when_present() {
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null, false);
        GeminiContent content = new GeminiContent(
                List.of(
                        GeminiContent.GeminiPart.builder()
                                .thoughtSignature("sig-1")
                                .toolCall(new GeminiContent.GeminiPart.GeminiToolCall(
                                        "GOOGLE_SEARCH_WEB",
                                        java.util.Map.of("queries", List.of("langchain4j")),
                                        "tool-1"))
                                .build(),
                        GeminiContent.GeminiPart.builder()
                                .thoughtSignature("sig-2")
                                .functionCall(new GeminiContent.GeminiPart.GeminiFunctionCall(
                                        "getWeather", java.util.Map.of("city", "Paris"), "fn-1"))
                                .build()),
                "model");

        builder.append(new GeminiGenerateContentResponse(
                "id-1", "gemini-pro", List.of(new GeminiCandidate(content, null, null, null)), null, null));

        ChatResponse completeResponse = builder.build();

        assertThat(completeResponse.aiMessage().attributes()).containsKey(PartsAndContentsMapper.RAW_PARTS_KEY);
    }

    @Test
    void should_preserve_server_tool_metadata_from_metadata_only_response() {
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null, true);
        GroundingMetadata groundingMetadata = GroundingMetadata.builder()
                .webSearchQueries(List.of("langchain4j"))
                .build();
        GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(
                "id-1", "gemini-pro", List.of(new GeminiCandidate(null, null, null, null)), null, groundingMetadata);

        TextAndTools result = builder.append(response);
        ChatResponse completeResponse = builder.build();

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.tools()).isEmpty();
        assertThat(completeResponse.aiMessage().attributes())
                .containsKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
    }

    @Test
    void should_preserve_exact_server_tool_result_shapes() {
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null, true);
        GeminiGenerateContentResponse.GeminiUrlContextMetadata urlContextMetadata =
                new GeminiGenerateContentResponse.GeminiUrlContextMetadata(
                        List.of(new GeminiGenerateContentResponse.GeminiUrlMetadata(
                                "https://docs.langchain4j.dev",
                                GeminiGenerateContentResponse.GeminiUrlRetrievalStatus.URL_RETRIEVAL_STATUS_SUCCESS)));
        GeminiContent content = new GeminiContent(
                List.of(
                        GeminiContent.GeminiPart.builder()
                                .toolCall(new GeminiContent.GeminiPart.GeminiToolCall(
                                        "GOOGLE_SEARCH_WEB",
                                        java.util.Map.of("queries", List.of("langchain4j")),
                                        "search-tool-1"))
                                .build(),
                        GeminiContent.GeminiPart.builder()
                                .toolCall(new GeminiContent.GeminiPart.GeminiToolCall(
                                        "URL_CONTEXT",
                                        java.util.Map.of("url", "https://docs.langchain4j.dev"),
                                        "url-tool-1"))
                                .build(),
                        GeminiContent.GeminiPart.builder()
                                .toolCall(new GeminiContent.GeminiPart.GeminiToolCall(
                                        "GOOGLE_MAPS", java.util.Map.of("query", "Paris landmark"), "maps-tool-1"))
                                .build()),
                "model");
        GroundingMetadata groundingMetadata = GroundingMetadata.builder()
                .webSearchQueries(List.of("langchain4j"))
                .googleMapsWidgetContextToken("widget-token")
                .groundingChunks(List.of(new GroundingMetadata.GroundingChunk(
                        new GroundingMetadata.GroundingChunk.Web("https://example.com", "Example"),
                        null,
                        new GroundingMetadata.GroundingChunk.Maps(
                                "https://maps.example.com", "Paris", "Landmark", "place-1", null))))
                .build();
        GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(
                "id-1",
                "gemini-pro",
                List.of(new GeminiCandidate(content, null, urlContextMetadata, groundingMetadata)),
                null,
                null);

        builder.append(response);

        ChatResponse completeResponse = builder.build();

        @SuppressWarnings("unchecked")
        List<GoogleAiGeminiServerToolResult> results = (List<GoogleAiGeminiServerToolResult>)
                completeResponse.aiMessage().attributes().get(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);

        assertThat(results)
                .extracting(GoogleAiGeminiServerToolResult::type)
                .contains("url_context_tool_result", "google_search_tool_result", "google_maps_tool_result");
        assertThat(results.stream()
                        .filter(result -> "url_context_tool_result".equals(result.type()))
                        .findFirst())
                .isPresent()
                .get()
                .satisfies(result -> {
                    assertThat(result.toolUseId()).isEqualTo("url-tool-1");
                    assertThat((java.util.Map<String, Object>) result.content()).containsKey("url_metadata");
                });
        assertThat(results.stream()
                        .filter(result -> "google_search_tool_result".equals(result.type()))
                        .findFirst())
                .isPresent()
                .get()
                .satisfies(result -> {
                    assertThat(result.toolUseId()).isEqualTo("search-tool-1");
                    assertThat((java.util.Map<String, Object>) result.content())
                            .containsEntry("web_search_queries", List.of("langchain4j"));
                });
        assertThat(results.stream()
                        .filter(result -> "google_maps_tool_result".equals(result.type()))
                        .findFirst())
                .isPresent()
                .get()
                .satisfies(result -> {
                    assertThat(result.toolUseId()).isEqualTo("maps-tool-1");
                    assertThat((java.util.Map<String, Object>) result.content())
                            .containsEntry("google_maps_widget_context_token", "widget-token");
                });
    }

    @Test
    void should_group_code_execution_results_by_tool_use_id() {
        GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null, true);
        GeminiContent content = new GeminiContent(
                List.of(
                        GeminiContent.GeminiPart.builder()
                                .executableCode(new GeminiContent.GeminiPart.GeminiExecutableCode(
                                        GeminiContent.GeminiPart.GeminiExecutableCode.GeminiLanguage.PYTHON,
                                        "print(1)",
                                        "code-1"))
                                .codeExecutionResult(new GeminiContent.GeminiPart.GeminiCodeExecutionResult(
                                        GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome.OUTCOME_OK,
                                        "1",
                                        "code-1"))
                                .build(),
                        GeminiContent.GeminiPart.builder()
                                .executableCode(new GeminiContent.GeminiPart.GeminiExecutableCode(
                                        GeminiContent.GeminiPart.GeminiExecutableCode.GeminiLanguage.PYTHON,
                                        "print(2)",
                                        "code-2"))
                                .codeExecutionResult(new GeminiContent.GeminiPart.GeminiCodeExecutionResult(
                                        GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome.OUTCOME_OK,
                                        "2",
                                        "code-2"))
                                .build()),
                "model");

        builder.append(new GeminiGenerateContentResponse(
                "id-1", "gemini-pro", List.of(new GeminiCandidate(content, null, null, null)), null, null));

        ChatResponse completeResponse = builder.build();

        @SuppressWarnings("unchecked")
        List<GoogleAiGeminiServerToolResult> results = (List<GoogleAiGeminiServerToolResult>)
                completeResponse.aiMessage().attributes().get(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);

        assertThat(results)
                .filteredOn(result -> "code_execution_tool_result".equals(result.type()))
                .extracting(GoogleAiGeminiServerToolResult::toolUseId)
                .containsExactly("code-1", "code-2");
    }
}
