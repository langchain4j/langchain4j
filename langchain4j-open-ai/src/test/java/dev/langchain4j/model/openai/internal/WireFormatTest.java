package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the wire format now that the snake_case naming strategy lives on the codec rather
 * than on each DTO via {@code @JsonNaming}. A regression here would silently send OpenAI
 * field names it does not recognize.
 */
class WireFormatTest {

    @Test
    void chat_request_fields_are_snake_case() {
        String json = Json.toJson(ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hello")
                .maxTokens(100)
                .maxCompletionTokens(200)
                .topP(0.9)
                .presencePenalty(0.1)
                .frequencyPenalty(0.2)
                .parallelToolCalls(true)
                .reasoningEffort("low")
                .serviceTier("auto")
                .topLogprobs(3)
                .build());

        assertThat(json)
                .contains("\"max_tokens\"")
                .contains("\"max_completion_tokens\"")
                .contains("\"top_p\"")
                .contains("\"presence_penalty\"")
                .contains("\"frequency_penalty\"")
                .contains("\"parallel_tool_calls\"")
                .contains("\"reasoning_effort\"")
                .contains("\"service_tier\"")
                .contains("\"top_logprobs\"");

        assertThat(json)
                .doesNotContain("maxTokens")
                .doesNotContain("maxCompletionTokens")
                .doesNotContain("topP")
                .doesNotContain("presencePenalty")
                .doesNotContain("parallelToolCalls");
    }

    @Test
    void embedding_request_fields_are_snake_case() {
        String json = Json.toJson(EmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .input(List.of("a"))
                .dimensions(256)
                .build());

        assertThat(json).contains("\"model\"").contains("\"dimensions\"").contains("\"input\"");
    }

    @Test
    void explicitly_named_properties_are_left_alone() {
        // @JsonProperty("...") values must not be re-cased by the naming strategy
        String json = Json.toJson(ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hi")
                .build());
        assertThat(json).contains("\"role\" : \"user\"");
    }
}
