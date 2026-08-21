package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the wire format now that the snake_case naming strategy lives on the codec rather than
 * on each DTO via {@code @JsonNaming}. No Ollama DTO declares explicit {@code @JsonProperty}
 * names, so the naming strategy is the only thing producing correct field names.
 */
class OllamaWireFormatTest {

    @Test
    void options_fields_are_snake_case() {
        String json = OllamaJsonUtils.toJsonWithoutIdent(Options.builder()
                .numPredict(10)
                .numCtx(256)
                .topK(40)
                .topP(0.9)
                .minP(0.05)
                .repeatPenalty(1.1)
                .mirostatEta(0.1)
                .mirostatTau(5.0)
                .build());

        assertThat(json)
                .contains("\"num_predict\":10")
                .contains("\"num_ctx\":256")
                .contains("\"top_k\":40")
                .contains("\"top_p\":0.9")
                .contains("\"min_p\":0.05")
                .contains("\"repeat_penalty\":1.1")
                .contains("\"mirostat_eta\":0.1")
                .contains("\"mirostat_tau\":5.0");

        assertThat(json)
                .doesNotContain("numPredict")
                .doesNotContain("numCtx")
                .doesNotContain("topK")
                .doesNotContain("repeatPenalty");
    }

    @Test
    void chat_request_fields_are_snake_case() {
        String json = OllamaJsonUtils.toJsonWithoutIdent(OllamaChatRequest.builder()
                .model("llama3")
                .messages(List.of(Message.builder()
                        .role(Role.USER)
                        .content("hi")
                        .build()))
                .keepAlive(300)
                .build());

        assertThat(json).contains("\"keep_alive\":300").doesNotContain("keepAlive");
    }

    @Test
    void responses_are_read_from_snake_case() {
        OllamaChatResponse response = OllamaJsonUtils.fromJson(
                "{\"model\":\"llama3\",\"created_at\":\"2026-01-01T00:00:00Z\",\"done\":true,"
                        + "\"done_reason\":\"stop\",\"eval_count\":7,\"prompt_eval_count\":3}",
                OllamaChatResponse.class);

        assertThat(response.getModel()).isEqualTo("llama3");
        assertThat(response.getDoneReason()).isEqualTo("stop");
        assertThat(response.getEvalCount()).isEqualTo(7);
        assertThat(response.getPromptEvalCount()).isEqualTo(3);
    }
}
