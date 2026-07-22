package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.reactive.streaming.AbstractStreamingChatModelPublisherNonBlockingIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * OpenAI binding of the shared reactive non-blocking TCK ({@link AbstractStreamingChatModelPublisherNonBlockingIT}).
 * OpenAI streams via {@code HttpClient.stream()} → the JDK client's {@code BodyHandlers.ofPublisher()}, so a streamed
 * response is parsed and dispatched on the JDK HTTP client's workers ({@code HttpClient-*}, the base's default policed
 * threads) with nothing parked.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiStreamingChatModelPublisherNonBlockingIT extends AbstractStreamingChatModelPublisherNonBlockingIT {

    @Override
    protected StreamingChatModel newModel(boolean logging) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(200)
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }
}
