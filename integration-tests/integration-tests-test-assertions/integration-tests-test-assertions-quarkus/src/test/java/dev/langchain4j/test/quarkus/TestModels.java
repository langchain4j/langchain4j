package dev.langchain4j.test.quarkus;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Provides the models of the application in tests. They are application-scoped, so a test can replace them with
 * {@code QuarkusMock.installMockForType(...)}: the AI Services, created once, then use the replacement for that test.
 */
public class TestModels {

    static final String DEFAULT_ANSWER = "How can I help you?";

    @Produces
    @ApplicationScoped
    ChatModel chatModel() {
        return MockChatModel.thatResponds(DEFAULT_ANSWER).thenRepeats();
    }

    @Produces
    @ApplicationScoped
    StreamingChatModel streamingChatModel() {
        return MockStreamingChatModel.thatStreams("How ", "can ", "I ", "help ", "you?")
                .thenRepeats();
    }
}
