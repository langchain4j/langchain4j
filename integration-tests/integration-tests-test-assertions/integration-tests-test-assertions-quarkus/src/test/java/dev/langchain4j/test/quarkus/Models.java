package dev.langchain4j.test.quarkus;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import io.quarkus.test.junit.QuarkusMock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Installs the model mocks of langchain4j-test as the model beans of the application for the current test.
 */
final class Models {

    private static final AtomicInteger IDS = new AtomicInteger();

    private Models() {}

    static ChatModelMock useChatModel(ChatModelMock chatModel) {
        QuarkusMock.installMockForType(chatModel, ChatModel.class);
        return chatModel;
    }

    static ChatModelMock useChatModelResponding(AiMessage... responses) {
        return useChatModel(ChatModelMock.thatResponds(List.of(responses)));
    }

    static StreamingChatModelMock useStreamingChatModel(StreamingChatModelMock streamingChatModel) {
        QuarkusMock.installMockForType(streamingChatModel, StreamingChatModel.class);
        return streamingChatModel;
    }

    static AiMessage toolCall(String toolName, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("call-" + IDS.incrementAndGet())
                .name(toolName)
                .arguments(arguments)
                .build());
    }
}
