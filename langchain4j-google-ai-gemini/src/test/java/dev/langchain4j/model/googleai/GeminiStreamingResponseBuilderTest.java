package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiStreamingResponseBuilder.TextAndTools;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiStreamingResponseBuilderTest {

    private final GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null);

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
                List.of(new GeminiContent.GeminiPart("Hello", null, null, null, null, null, null, null, null, null)),
                "model");
        GeminiCandidate candidate = new GeminiCandidate(content, null, null, null);
        GeminiGenerateContentResponse response =
                new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).hasValue("Hello");
        assertThat(result.tools()).isEmpty();
    }
}
