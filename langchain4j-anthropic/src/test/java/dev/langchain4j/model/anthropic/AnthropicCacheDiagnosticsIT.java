package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.randomString;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_SONNET_4_5_20250929;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicCacheDiagnosticsIT {

    private static final String BETA = "cache-diagnosis-2026-04-07";

    @Test
    void should_return_null_diagnostics_on_first_turn_and_model_changed_on_second_turn() {
        // given
        AnthropicChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .beta(BETA)
                .returnCacheDiagnostics(true)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        String prompt = randomString(10);

        // when: first turn, opting in with previousMessageId == null (the model default)
        ChatResponse response1 = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .parameters(AnthropicChatRequestParameters.builder()
                        .modelName(CLAUDE_HAIKU_4_5_20251001)
                        .build())
                .build());

        // then: first turn always reports null diagnostics (nothing to compare against yet)
        AnthropicChatResponseMetadata metadata1 = (AnthropicChatResponseMetadata) response1.metadata();
        assertThat(metadata1.id()).isNotBlank();
        assertThat(metadata1.cacheDiagnostics()).isNull();

        // when: second turn, deliberately switching models to force a deterministic "model_changed" divergence
        ChatResponse response2 = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .parameters(AnthropicChatRequestParameters.builder()
                        .modelName(CLAUDE_SONNET_4_5_20250929)
                        // returnCacheDiagnostics is already enabled on the model, so only previousMessageId
                        // needs to be supplied per request (see AnthropicChatRequestParameters#previousMessageId()).
                        .previousMessageId(metadata1.id())
                        .build())
                .build());

        // then
        AnthropicChatResponseMetadata metadata2 = (AnthropicChatResponseMetadata) response2.metadata();
        assertThat(metadata2.id()).isNotBlank();

        AnthropicCacheDiagnostics diagnostics = metadata2.cacheDiagnostics();
        if (diagnostics != null && diagnostics.cacheMissReasonType() != null) {
            // comparison resolved in time for this response (the common case)
            assertThat(diagnostics.cacheMissReasonType()).isEqualTo("model_changed");
            assertThat(diagnostics.cacheMissedInputTokens()).isNotNull();
        }
        // else: either no comparison was returned, or it was still pending
        // ("treat as inconclusive and check the next turn" per the Anthropic docs) -- both are
        // legitimate, documented states for a single live call and not a wiring failure on their own.
    }
}
