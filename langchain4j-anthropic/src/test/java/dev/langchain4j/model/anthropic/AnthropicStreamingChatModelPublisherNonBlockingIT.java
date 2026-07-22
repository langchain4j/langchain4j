package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.reactive.streaming.AbstractStreamingChatModelPublisherNonBlockingIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Anthropic binding of the shared reactive non-blocking TCK ({@link AbstractStreamingChatModelPublisherNonBlockingIT}).
 * Anthropic streams over the {@code HttpClient} abstraction's reactive {@code stream()} publisher (the JDK client's
 * {@code BodyHandlers.ofPublisher()}), so a streamed response is parsed and dispatched on the JDK HTTP client's
 * workers ({@code HttpClient-*}, the base's default policed threads) with nothing parked.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingChatModelPublisherNonBlockingIT extends AbstractStreamingChatModelPublisherNonBlockingIT {

    @Override
    protected StreamingChatModel newModel(boolean logging) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(200)
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }
}
