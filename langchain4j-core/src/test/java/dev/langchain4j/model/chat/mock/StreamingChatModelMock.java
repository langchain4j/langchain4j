package dev.langchain4j.model.chat.mock;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

/**
 * An implementation of a {@link StreamingChatModel} useful for unit testing.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
@Experimental
public class StreamingChatModelMock implements StreamingChatModel {

    private final Queue<AiMessage> aiMessages;

    public StreamingChatModelMock(List<String> tokens) {
        this(List.of(toAiMessage(tokens)));
    }

    public StreamingChatModelMock(Collection<AiMessage> aiMessages) {
        this.aiMessages = new ConcurrentLinkedQueue<>(ensureNotEmpty(aiMessages, "aiMessages"));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        AiMessage aiMessage = ensureNotNull(aiMessages.poll(), "aiMessage");

        new Thread(() -> {
                    toTokens(aiMessage).forEach(token -> onPartialResponse(handler, token));

                    for (int i = 0; i < aiMessage.toolExecutionRequests().size(); i++) {
                        ToolExecutionRequest toolExecutionRequest =
                                aiMessage.toolExecutionRequests().get(i);
                        CompleteToolCall completeToolCall = new CompleteToolCall(i, toolExecutionRequest);
                        onCompleteToolCall(handler, completeToolCall);
                    }

                    ChatResponse chatResponse =
                            ChatResponse.builder().aiMessage(aiMessage).build();
                    onCompleteResponse(handler, chatResponse);
                })
                .start();
    }

    private static AiMessage toAiMessage(List<String> tokens) {
        String text = String.join("", tokens);
        return AiMessage.from(text);
    }

    static List<String> toTokens(AiMessage aiMessage) {
        if (isNullOrEmpty(aiMessage.text())) {
            return List.of();
        }

        // approximating: each char will become a token
        return aiMessage.text().chars()
                .mapToObj(c -> String.valueOf((char) c))
                .toList();
    }

    public static StreamingChatModelMock thatAlwaysStreams(String... tokens) {
        return new StreamingChatModelMock(asList(tokens));
    }

    public static StreamingChatModelMock thatAlwaysStreams(List<String> tokens) {
        return new StreamingChatModelMock(tokens);
    }

    public static StreamingChatModelMock thatAlwaysStreams(AiMessage aiMessage) {
        return new StreamingChatModelMock(List.of(aiMessage));
    }

    public static StreamingChatModelMock thatAlwaysStreams(AiMessage... aiMessages) {
        return new StreamingChatModelMock(asList(aiMessages));
    }

    public static StreamingChatModelMock thatAlwaysStreams(Collection<AiMessage> aiMessages) {
        return new StreamingChatModelMock(aiMessages);
    }
}
