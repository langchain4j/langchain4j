package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.reactive.streaming.AbstractStreamingChatModelPublisherNonBlockingIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Bedrock binding of the shared reactive non-blocking TCK ({@link AbstractStreamingChatModelPublisherNonBlockingIT}).
 * Bedrock streams over the AWS SDK v2's native async client (Netty), delivering {@code converseStream} events through
 * our pipeline on the SDK's async-response executor ({@code sdk-async-response-*}) — the same threads
 * {@link BedrockChatModelNonBlockingIT} polices for the {@code chatAsync} path. (The Netty event-loop threads that
 * read the socket are the SDK's own non-blocking I/O and are not policed here.)
 */
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelPublisherNonBlockingIT extends AbstractStreamingChatModelPublisherNonBlockingIT {

    @Override
    protected StreamingChatModel newModel(boolean logging) {
        return BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected String policedThreadNamePrefix() {
        return "sdk-async-response";
    }
}
