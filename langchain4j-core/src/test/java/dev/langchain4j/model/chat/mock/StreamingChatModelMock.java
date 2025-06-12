package dev.langchain4j.model.chat.mock;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

/**
 * An implementation of a {@link StreamingChatModel} useful for unit testing.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
@Experimental
public class StreamingChatModelMock implements StreamingChatModel {

    private final List<String> tokens;

    public StreamingChatModelMock(List<String> tokens) {
        this.tokens = copy(ensureNotEmpty(tokens, "tokens"));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        tokens.forEach(handler::onPartialResponse);
        AiMessage aiMessage = AiMessage.from(String.join("", tokens));
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .build();
        handler.onCompleteResponse(chatResponse);
    }

    public static StreamingChatModelMock thatAlwaysStreams(String... tokens) {
        return new StreamingChatModelMock(asList(tokens));
    }

    public static StreamingChatModelMock thatAlwaysStreams(List<String> tokens) {
        return new StreamingChatModelMock(tokens);
    }
}
